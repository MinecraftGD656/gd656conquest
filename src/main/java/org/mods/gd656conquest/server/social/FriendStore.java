package org.mods.gd656conquest.server.social;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.mods.gd656conquest.server.social.party.domain.PartyError;

/** UUID relationships live in one world-owned file. A successful write commits both sides together. */
public final class FriendStore {
    public static final int MAX_FRIENDS=256,MAX_REQUESTS=32;
    private static final int MAX_PROFILES=8192,MAX_LINKS=65536,MAX_PENDING=4096,MAX_BYTES=8*1024*1024;
    private static final Gson JSON=new Gson();
    public record Profile(UUID id,String accountName,String displayName) {}
    public record Request(String id,UUID from,UUID to) {}
    private record Link(UUID first,UUID second) {}
    private record FileData(int version,List<Profile> profiles,List<Link> friendships,List<Request> requests) {}
    private static final class State {
        final Map<UUID,Profile> profiles=new LinkedHashMap<>();
        final Map<UUID,Set<UUID>> friends=new LinkedHashMap<>();
        final Map<String,Request> requests=new LinkedHashMap<>();
        final Map<UUID,Set<String>> requestsByPlayer=new HashMap<>();
        State() {}
        State(State source){profiles.putAll(source.profiles);source.friends.forEach((id,values)->friends.put(id,new LinkedHashSet<>(values)));requests.putAll(source.requests);source.requestsByPlayer.forEach((id,values)->requestsByPlayer.put(id,new LinkedHashSet<>(values)));}
        Set<UUID> friends(UUID id){return friends.getOrDefault(id,Set.of());}
        Set<String> pending(UUID id){return requestsByPlayer.getOrDefault(id,Set.of());}
        void add(Request request){requests.put(request.id(),request);requestsByPlayer.computeIfAbsent(request.from(),id->new LinkedHashSet<>()).add(request.id());requestsByPlayer.computeIfAbsent(request.to(),id->new LinkedHashSet<>()).add(request.id());}
        void remove(Request request){requests.remove(request.id());for(UUID id:List.of(request.from(),request.to())){var ids=requestsByPlayer.get(id);if(ids!=null){ids.remove(request.id());if(ids.isEmpty())requestsByPlayer.remove(id);}}}
        void link(UUID a,UUID b){friends.computeIfAbsent(a,id->new LinkedHashSet<>()).add(b);friends.computeIfAbsent(b,id->new LinkedHashSet<>()).add(a);}
        void prune(){profiles.keySet().removeIf(id->friends(id).isEmpty()&&pending(id).isEmpty());friends.entrySet().removeIf(e->e.getValue().isEmpty());}
    }
    private final Path file;
    private State state;
    private byte[] saved;

    public FriendStore(Path directory)throws IOException {
        file=directory.resolve("friends.json").toAbsolutePath().normalize();
        saved=readFile();state=saved==null?new State():decode(saved);
    }
    public Path file(){return file;}
    public synchronized List<Profile> friends(UUID id){return state.friends(id).stream().map(state.profiles::get).toList();}
    public synchronized List<Request> requests(UUID id){return state.pending(id).stream().map(state.requests::get).toList();}
    public synchronized Profile profile(UUID id){return state.profiles.get(id);}
    public synchronized boolean areFriends(UUID a,UUID b){return state.friends(a).contains(b);}
    public synchronized Request request(Profile from,Profile to)throws IOException {
        validateProfile(from);validateProfile(to);
        if(from.id().equals(to.id()))throw new PartyError("请选择其他玩家");
        if(areFriends(from.id(),to.id()))throw new PartyError("你们已经是好友");
        if(state.friends(from.id()).size()>=MAX_FRIENDS||state.friends(to.id()).size()>=MAX_FRIENDS)throw new PartyError("好友列表已满");
        for(Request request:requests(from.id()))if(request.from().equals(to.id()))throw new PartyError("对方已向你发送好友申请，请在申请列表中接受");else if(request.to().equals(to.id()))throw new PartyError("好友申请已经发出");
        if(state.pending(from.id()).size()>=MAX_REQUESTS||state.pending(to.id()).size()>=MAX_REQUESTS)throw new PartyError("待处理好友申请已满，请先处理现有申请");
        if(state.requests.size()>=MAX_PENDING)throw new PartyError("服务器待处理好友申请已满");
        State next=new State(state);next.profiles.put(from.id(),from);next.profiles.put(to.id(),to);
        if(next.profiles.size()>MAX_PROFILES)throw new PartyError("服务器好友记录已满，请联系服主");
        Request request=new Request(UUID.randomUUID().toString(),from.id(),to.id());next.add(request);commit(next);return request;
    }
    public synchronized Request resolve(UUID actor,String id,String operation)throws IOException {
        Request request=state.requests.get(id);if(request==null)throw new PartyError("好友申请已结束");
        boolean cancel=operation.equals("FRIEND_CANCEL");
        if(!(cancel?request.from():request.to()).equals(actor))throw new PartyError("这条好友申请属于其他玩家");
        if(!Set.of("FRIEND_ACCEPT","FRIEND_REFUSE","FRIEND_CANCEL").contains(operation))throw new PartyError("未知的好友操作");
        State next=new State(state);
        if(operation.equals("FRIEND_ACCEPT")){
            if(next.friends(request.from()).size()>=MAX_FRIENDS||next.friends(request.to()).size()>=MAX_FRIENDS)throw new PartyError("好友列表已满");
            if(next.friends.values().stream().mapToInt(Set::size).sum()/2>=MAX_LINKS)throw new PartyError("服务器好友记录已满，请联系服主");
            next.link(request.from(),request.to());
        }
        next.remove(request);next.prune();commit(next);return request;
    }
    public synchronized void remove(UUID actor,UUID target)throws IOException {
        if(!areFriends(actor,target))throw new PartyError("该玩家已不在好友列表中");
        State next=new State(state);next.friends.get(actor).remove(target);next.friends.get(target).remove(actor);next.prune();commit(next);
    }
    public synchronized void refresh(Profile profile)throws IOException {
        validateProfile(profile);if(!state.profiles.containsKey(profile.id())||profile.equals(state.profiles.get(profile.id())))return;
        State next=new State(state);next.profiles.put(profile.id(),profile);commit(next);
    }
    private byte[] readFile()throws IOException {
        if(Files.isSymbolicLink(file)||Files.isSymbolicLink(file.getParent()))throw new IOException("Social data path is a symbolic link: "+file);
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_BYTES)throw new IOException("Invalid social data file: "+file);
        try(var input=Files.newInputStream(file)){byte[] bytes=input.readNBytes(MAX_BYTES+1);if(bytes.length>MAX_BYTES)throw new IOException("Social data exceeds file limit: "+file);return bytes;}
    }
    private void commit(State next)throws IOException {
        byte[] bytes=encode(next);if(bytes.length>MAX_BYTES)throw new IOException("Social data exceeds file limit: "+file);
        if(!Arrays.equals(saved,readFile()))throw new IOException("Social data changed outside the server; original file preserved: "+file);
        Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"friends-",".tmp");
        try{
            try(FileChannel channel=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            state=next;saved=bytes;
        }finally{Files.deleteIfExists(temporary);}
    }
    private static byte[] encode(State value){
        List<Link> links=new ArrayList<>();value.friends.forEach((a,ids)->ids.forEach(b->{if(a.compareTo(b)<0)links.add(new Link(a,b));}));
        return JSON.toJson(new FileData(1,List.copyOf(value.profiles.values()),links,List.copyOf(value.requests.values()))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private State decode(byte[] bytes)throws IOException {
        try{
            FileData fileData=JSON.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),FileData.class);
            if(fileData==null||fileData.version()!=1)throw new IllegalArgumentException("version");
            bounded(fileData.profiles(),MAX_PROFILES);bounded(fileData.friendships(),MAX_LINKS);bounded(fileData.requests(),MAX_PENDING);State value=new State();
            for(Profile p:fileData.profiles()){validateProfile(p);if(value.profiles.put(p.id(),p)!=null)throw new IllegalArgumentException("duplicate profile");}
            for(Link link:fileData.friendships()){
                if(link==null||link.first()==null||link.second()==null||link.first().equals(link.second())||!value.profiles.containsKey(link.first())||!value.profiles.containsKey(link.second())||value.friends(link.first()).contains(link.second()))throw new IllegalArgumentException("friendship identity");
                value.link(link.first(),link.second());if(value.friends(link.first()).size()>MAX_FRIENDS||value.friends(link.second()).size()>MAX_FRIENDS)throw new IllegalArgumentException("friend count");
            }
            Set<String> pairs=new HashSet<>();
            for(Request r:fileData.requests()){
                if(r==null||r.id()==null||!UUID.fromString(r.id()).toString().equals(r.id())||r.from()==null||r.to()==null||r.from().equals(r.to())||!value.profiles.containsKey(r.from())||!value.profiles.containsKey(r.to())||value.requests.containsKey(r.id())||value.friends(r.from()).contains(r.to()))throw new IllegalArgumentException("request identity");
                String pair=r.from().compareTo(r.to())<0?r.from()+":"+r.to():r.to()+":"+r.from();if(!pairs.add(pair))throw new IllegalArgumentException("duplicate request pair");
                value.add(r);if(value.pending(r.from()).size()>MAX_REQUESTS||value.pending(r.to()).size()>MAX_REQUESTS)throw new IllegalArgumentException("request count");
            }
            value.prune();return value;
        }catch(IllegalArgumentException|JsonParseException e){throw new IOException("Invalid social data; original file preserved: "+file,e);}
    }
    private static void validateProfile(Profile p){if(p==null||p.id()==null||p.accountName()==null||p.accountName().isBlank()||p.accountName().length()>64||p.displayName()==null||p.displayName().isBlank()||p.displayName().length()>64)throw new IllegalArgumentException("profile");}
    private static void bounded(List<?> values,int maximum){if(values==null||values.size()>maximum)throw new IllegalArgumentException("file list limit");}
}
