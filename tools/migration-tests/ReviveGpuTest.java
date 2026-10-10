package tests;

import com.google.gson.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.mods.gd656conquest.client.room.ReviveTransition;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Execute the shipped five-pass graph on a real GPU, including half-size bloom and final blit. */
public final class ReviveGpuTest {
    static int checks,vao,vbo;
    static final Map<String,Integer> programs=new HashMap<>();
    static final Map<String,JsonObject> configs=new HashMap<>();
    record Surface(int texture,int fbo,int width,int height) {}
    static String resource(Path root,String name)throws Exception {
        if(Files.isDirectory(root))return Files.readString(root.resolve(name));
        try(ZipFile zip=new ZipFile(root.toFile())) {
            return new String(zip.getInputStream(zip.getEntry("assets/gd656conquest/shaders/"+name)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static int shader(int kind,String source) {
        int id=glCreateShader(kind);glShaderSource(id,source);glCompileShader(id);
        check(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,"Actual GLSL compiles: "+glGetShaderInfoLog(id));return id;
    }
    static void program(String name,String vertex,String fragment) {
        int id=glCreateProgram(),vs=shader(GL_VERTEX_SHADER,vertex),fs=shader(GL_FRAGMENT_SHADER,fragment);
        glAttachShader(id,vs);glAttachShader(id,fs);glLinkProgram(id);
        check(glGetProgrami(id,GL_LINK_STATUS)==GL_TRUE,"Program links: "+glGetProgramInfoLog(id));
        glDeleteShader(vs);glDeleteShader(fs);programs.put(name,id);
    }
    static Surface surface(int w,int h) {
        int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_FLOAT,(FloatBuffer)null);
        int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"LDR world framebuffer complete");
        return new Surface(texture,fbo,w,h);
    }
    static void fill(Surface s) {
        FloatBuffer pixels=BufferUtils.createFloatBuffer(s.width*s.height*4);
        for(int y=0;y<s.height;y++)for(int x=0;x<s.width;x++) {
            float c=.035F;
            if(x<s.width/8)c=0; // Dark silhouette stays distinguishable even at peak.
            if(Math.abs(x-s.width/2)<=2&&Math.abs(y-s.height/2)<=2)c=.95F;
            pixels.put(c).put(c).put(c).put(.6F);
        }
        pixels.flip();glBindTexture(GL_TEXTURE_2D,s.texture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,s.width,s.height,GL_RGBA,GL_FLOAT,pixels);
    }
    static void fillSky(Surface s) {
        FloatBuffer pixels=BufferUtils.createFloatBuffer(s.width*s.height*4);
        for(int y=0;y<s.height;y++)for(int x=0;x<s.width;x++) {
            int band=Math.min(3,x*4/s.width);
            pixels.put(.12F).put(.24F).put(.48F).put(new float[]{0,.25F,.6F,1}[band]);
        }
        pixels.flip();glBindTexture(GL_TEXTURE_2D,s.texture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,s.width,s.height,GL_RGBA,GL_FLOAT,pixels);
    }
    static void draw(JsonObject pass,Map<String,Surface> targets,float pulse,boolean bloomEnabled) {
        String name=pass.get("name").getAsString();int program=programs.get(name);
        Surface input=targets.get(pass.get("intarget").getAsString()),out=targets.get(pass.get("outtarget").getAsString());
        check(input.texture!=out.texture,"No texture feedback loop in "+name);
        glUseProgram(program);glBindFramebuffer(GL_FRAMEBUFFER,out.fbo);glViewport(0,0,out.width,out.height);
        // PostPass clears its output; EffectInstance applies the program JSON blend state.
        // A transparent world pixel still has valid RGB, which an alpha-blended copy loses.
        glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
        JsonObject blend=configs.get(name).getAsJsonObject("blend");
        check(blend.get("func").getAsString().equals("add"),"Supported shipped blend equation");
        glBlendEquation(GL_FUNC_ADD);
        String src=blend.get("srcrgb").getAsString(),dst=blend.get("dstrgb").getAsString();
        if(src.equals("one")&&dst.equals("zero"))glDisable(GL_BLEND);
        else {
            check(src.equals("srcalpha")&&dst.equals("1-srcalpha"),"Supported native alpha blend");
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        }
        glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,new float[]{2F/out.width,0,0,0,0,2F/out.height,0,0,0,0,-1,0,-1,-1,0,1});
        glUniform2f(glGetUniformLocation(program,"InSize"),input.width,input.height);
        glUniform2f(glGetUniformLocation(program,"OutSize"),out.width,out.height);
        glUniform4f(glGetUniformLocation(program,"ColorModulate"),1,1,1,1);
        glUniform1f(glGetUniformLocation(program,"ExposureEV"),ReviveTransition.EXPOSURE_EV*pulse);
        glUniform1f(glGetUniformLocation(program,"BloomGain"),bloomEnabled?ReviveTransition.BLOOM_GAIN*pulse:0);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,input.texture);glUniform1i(glGetUniformLocation(program,"DiffuseSampler"),0);
        if(pass.has("auxtargets"))for(JsonElement element:pass.getAsJsonArray("auxtargets")) {
            JsonObject aux=element.getAsJsonObject();Surface s=targets.get(aux.get("id").getAsString());
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,s.texture);glUniform1i(glGetUniformLocation(program,aux.get("name").getAsString()),1);
        }
        if(pass.has("uniforms"))for(JsonElement element:pass.getAsJsonArray("uniforms")) {
            JsonObject uniform=element.getAsJsonObject();JsonArray values=uniform.getAsJsonArray("values");
            int location=glGetUniformLocation(program,uniform.get("name").getAsString());
            if(values.size()==1)glUniform1f(location,values.get(0).getAsFloat());
            else glUniform2f(location,values.get(0).getAsFloat(),values.get(1).getAsFloat());
        }
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,new float[]{0,0,out.width,0,out.width,out.height,0,0,out.width,out.height,0,out.height},GL_STATIC_DRAW);
        int position=glGetAttribLocation(program,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,2,GL_FLOAT,false,0,0L);
        glDrawArrays(GL_TRIANGLES,0,6);
    }
    static FloatBuffer read(Surface s) {
        glBindFramebuffer(GL_FRAMEBUFFER,s.fbo);FloatBuffer pixels=BufferUtils.createFloatBuffer(s.width*s.height*4);
        glReadPixels(0,0,s.width,s.height,GL_RGBA,GL_FLOAT,pixels);return pixels;
    }
    static float pixel(FloatBuffer p,int w,int x,int y){return p.get((y*w+x)*4);}
    static FloatBuffer run(JsonArray passes,Map<String,Surface> targets,float pulse,boolean bloom) {
        fill(targets.get("minecraft:main"));
        for(JsonElement element:passes)draw(element.getAsJsonObject(),targets,pulse,bloom);
        return read(targets.get("minecraft:main"));
    }
    static FloatBuffer runSky(JsonArray passes,Map<String,Surface> targets,float pulse) {
        fillSky(targets.get("minecraft:main"));
        for(JsonElement element:passes)draw(element.getAsJsonObject(),targets,pulse,true);
        return read(targets.get("minecraft:main"));
    }
    public static void main(String[] args)throws Exception {
        check(glfwInit(),"GLFW initializes");glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(32,32,"Revive GPU validation",0,0);check(window!=0,"Hidden GPU context available");
        try {
            glfwMakeContextCurrent(window);GL.createCapabilities();glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
            Path root=Path.of(args[1]);String vertex,blit;
            try(ZipFile zip=new ZipFile(args[0])) {
                vertex=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/program/blit.vsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                blit=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/program/blit.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                configs.put("blit",JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/program/blit.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            }
            for(String name:List.of("revive_bright","revive_blur","revive_world")) {
                program("gd656conquest:"+name,vertex,resource(root,"program/"+name+".fsh"));
                configs.put("gd656conquest:"+name,JsonParser.parseString(resource(root,"program/"+name+".json")).getAsJsonObject());
            }
            program("blit",vertex,blit);vao=glGenVertexArrays();vbo=glGenBuffers();
            JsonObject graph=JsonParser.parseString(resource(root,"post/revive_world.json")).getAsJsonObject();
            JsonArray passes=graph.getAsJsonArray("passes");check(passes.size()==5,"Extraction, two blur axes, exposure composite and final blit");
            for(int[] size:new int[][]{{64,32},{97,49}}) {
                int w=size[0],h=size[1];Map<String,Surface> targets=new HashMap<>();targets.put("minecraft:main",surface(w,h));
                for(JsonElement element:graph.getAsJsonArray("targets")) {
                    String name=element.isJsonPrimitive()?element.getAsString():element.getAsJsonObject().get("name").getAsString();
                    targets.put(name,surface(name.equals("revive_world_swap")?w:w/2,name.equals("revive_world_swap")?h:h/2));
                }
                Surface main=targets.get("minecraft:main");fill(main);FloatBuffer original=read(main),idle=run(passes,targets,0,true);
                for(int i=0;i<w*h*4;i++)check(Math.abs(idle.get(i)-original.get(i))<.005,"Zero-pulse graph preserves world and alpha");
                FloatBuffer exposureOnly=run(passes,targets,1,false),peak=run(passes,targets,1,true),tail=run(passes,targets,ReviveTransition.pulse(.75),true);
                int cx=w/2,cy=h/2;
                check(pixel(peak,w,cx,cy)>.97,"Highlights approach white at peak");
                check(pixel(peak,w,w/4,cy)>pixel(original,w,w/4,cy)+.04,"World exposure increases sharply");
                check(pixel(peak,w,cx+7,cy)>pixel(exposureOnly,w,cx+7,cy)+.02,"Bloom spreads horizontally outside the bright object");
                check(pixel(peak,w,cx,cy+7)>pixel(exposureOnly,w,cx,cy+7)+.02,"Bloom spreads vertically outside the bright object");
                check(pixel(peak,w,1,cy)<.025&&pixel(peak,w,w/4,cy)<.5,"Dark silhouettes survive; no opaque white cover");
                check(pixel(tail,w,w/4,cy)<pixel(peak,w,w/4,cy),"World exposure gradually decays through the tail");
                FloatBuffer sustained=run(passes,targets,ReviveTransition.pulse(.5),true);
                FloatBuffer late=run(passes,targets,ReviveTransition.pulse(1.1),true);
                check(pixel(sustained,w,w/4,cy)>pixel(tail,w,w/4,cy),"Visible exposure persists at half a second");
                check(pixel(late,w,w/4,cy)<pixel(tail,w,w/4,cy),"Late exposure smoothly approaches normal colors");
                FloatBuffer ended=run(passes,targets,ReviveTransition.pulse(1.4),true);
                check(Math.abs(pixel(ended,w,w/4,cy)-pixel(original,w,w/4,cy))<.005,"Full timeline ends with normal world colors");
                for(int i=0;i<w*h;i++) {
                    check(Math.abs(peak.get(i*4+3)-.6)<.005,"Source world alpha preserved at peak");
                    for(int channel=0;channel<3;channel++)check(Float.isFinite(peak.get(i*4+channel))&&peak.get(i*4+channel)>=0&&peak.get(i*4+channel)<=1,"Bounded finite color");
                }
                check(glGetError()==GL_NO_ERROR,"No OpenGL errors with odd/even resize dimensions");
                fillSky(main);FloatBuffer skyOriginal=read(main),skyIdle=runSky(passes,targets,0),skyPeak=runSky(passes,targets,1);
                for(int i=0;i<w*h*4;i++)check(Math.abs(skyIdle.get(i)-skyOriginal.get(i))<.005,"Zero-pulse copy preserves sky/partial/opaque RGBA");
                for(int i=0;i<w*h;i++) {
                    check(Math.abs(skyPeak.get(i*4+3)-skyOriginal.get(i*4+3))<.005,"Peak preserves all world alpha values including zero");
                    for(int channel=0;channel<3;channel++)
                        check(skyPeak.get(i*4+channel)>skyOriginal.get(i*4+channel)+.02,"Sky stays visible and exposed regardless of alpha");
                }
                // Keep the former pipeline as a negative control: native blit clears then alpha-blends.
                JsonArray legacy=passes.deepCopy();legacy.get(4).getAsJsonObject().addProperty("name","blit");
                FloatBuffer broken=runSky(legacy,targets,1);
                for(int channel=0;channel<3;channel++) {
                    check(broken.get((h/2*w+w/8)*4+channel)<.005,"Former native blit reproduces black sky with zero alpha");
                    check(broken.get((h/2*w+w*3/8)*4+channel)<skyPeak.get((h/2*w+w*3/8)*4+channel)*.3,"Former native blit incorrectly darkens partial-alpha world");
                }
                check(glGetError()==GL_NO_ERROR,"No OpenGL errors in sky alpha regression");
                for(Surface s:targets.values()){glDeleteFramebuffers(s.fbo);glDeleteTextures(s.texture);}
            }
            System.out.println("PASS shipped revival GLSL/five-pass LDR graph, bloom spread, silhouettes, alpha and resize: "+checks+"; "+glGetString(GL_RENDERER));
        } finally {glfwDestroyWindow(window);glfwTerminate();}
    }
}
