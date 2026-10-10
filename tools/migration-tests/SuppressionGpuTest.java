package tests;

import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Compiles the actual shipped GLSL and reads back pixels from a hidden OpenGL context. */
public final class SuppressionGpuTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static int shader(int kind,String source){
        int id=glCreateShader(kind);glShaderSource(id,source);glCompileShader(id);
        check(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,"GLSL compilation: "+glGetShaderInfoLog(id));return id;
    }
    public static void main(String[] args)throws Exception{
        check(glfwInit(),"GLFW initialization");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        long window=glfwCreateWindow(32,32,"Death shader validation",0,0);
        check(window!=0,"Hidden GPU context available");
        try{
            glfwMakeContextCurrent(window);GL.createCapabilities();
            String vertex;
            try(ZipFile zip=new ZipFile(args[0])){vertex=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/program/blit.vsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            int program=glCreateProgram();glAttachShader(program,shader(GL_VERTEX_SHADER,vertex));
            String fragment;
            try(ZipFile jar=new ZipFile(args[1])) {
                fragment=new String(jar.getInputStream(jar.getEntry("assets/gd656conquest/shaders/program/boundary_world.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var gson=new com.google.gson.Gson();
                var graph=gson.fromJson(new String(jar.getInputStream(jar.getEntry("assets/gd656conquest/shaders/post/boundary_world.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
                check(graph.getAsJsonArray("passes").size()==2,"shipped graph has grade and identity-copy passes");
                check(graph.getAsJsonArray("passes").get(1).getAsJsonObject().getAsJsonArray("uniforms").get(0).getAsJsonObject().getAsJsonArray("values").get(0).getAsFloat()==0,"second pass copies without grading twice");
                var settings=gson.fromJson(new String(jar.getInputStream(jar.getEntry("assets/gd656conquest/shaders/program/boundary_world.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
                check(settings.getAsJsonObject("blend").get("srcrgb").getAsString().equals("one")&&settings.getAsJsonObject("blend").get("dstrgb").getAsString().equals("zero"),"alpha-independent copy blend");
            }
            glAttachShader(program,shader(GL_FRAGMENT_SHADER,fragment));
            glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,"Program linking: "+glGetProgramInfoLog(program));glUseProgram(program);
            float[][] colors={{1,0,0},{0,1,0},{0,0,1},{1,1,1},{0,0,0},{.5F,.5F,.5F},{.01F,.02F,.03F},{.95F,.85F,.75F},{.3F,.5F,.7F}};
            int w=colors.length;
            FloatBuffer source=BufferUtils.createFloatBuffer(w*4);
            for(int i=0;i<w;i++)source.put(colors[i]).put(i%3==0?0:i%3==1?.6F:1);source.flip();
            int texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,w,1,0,GL_RGBA,GL_FLOAT,source);
            int output=glGenTextures();glBindTexture(GL_TEXTURE_2D,output);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA32F,w,1,0,GL_RGBA,GL_FLOAT,(FloatBuffer)null);
            int fbo=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,output,0);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Float pixel framebuffer complete");
            int vao=glGenVertexArrays();glBindVertexArray(vao);int vbo=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glBufferData(GL_ARRAY_BUFFER,new float[]{0,0,w,0,w,1,0,0,w,1,0,1},GL_STATIC_DRAW);
            int position=glGetAttribLocation(program,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,2,GL_FLOAT,false,0,0L);
            glUniformMatrix4fv(glGetUniformLocation(program,"ProjMat"),false,new float[]{2F/w,0,0,0,0,2,0,0,0,0,-1,0,-1,-1,0,1});
            glUniform2f(glGetUniformLocation(program,"OutSize"),w,1);glUniform1i(glGetUniformLocation(program,"DiffuseSampler"),0);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glViewport(0,0,w,1);glDisable(GL_BLEND);glDisable(GL_DEPTH_TEST);
            for(float strength:new float[]{0,.25F,.5F,.75F,1}) for(float suppression:new float[]{0,.25F,.6F}) {
                glUniform1f(glGetUniformLocation(program,"BoundaryStrength"),strength);
                glUniform1f(glGetUniformLocation(program,"SuppressionOpacity"),suppression);
                glDrawArrays(GL_TRIANGLES,0,6);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(w*4);glReadPixels(0,0,w,1,GL_RGBA,GL_FLOAT,pixels);
                for(int i=0;i<w;i++) {
                    float[] color=colors[i];
                    float lum=.2126F*color[0]+.7152F*color[1]+.0722F*color[2];
                    float x=((i+.5F)/w-.5F)*2;
                    float t=Math.max(0,Math.min(1,(x*x-.25F)/1.05F));
                    float vignette=t*t*(3-2*t);
                    float[] warm={1.04F,.98F,.90F};
                    for(int channel=0;channel<3;channel++) {
                        float muted=(color[channel]*.28F+lum*.72F)*warm[channel];
                        float graded=Math.max(0,Math.min(1,(muted-.5F)*1.04F+.5F))*.96F;
                        float edgeT=Math.max(0,Math.min(1,(Math.abs(x)-.49F)/.51F));
                        float edge=edgeT*edgeT*(3-2*edgeT);
                        float expected=(color[channel]*(1-strength)+graded*strength)*(1-Math.max(.28F*vignette*strength,suppression*edge));
                        check(Math.abs(pixels.get(i*4+channel)-expected)<.0002F,"shipped GPU grade matches sample "+i+" / strength "+strength);
                    }
                    float alpha=i%3==0?0:i%3==1?.6F:1;
                    check(Math.abs(pixels.get(i*4+3)-alpha)<.0001F,"source alpha preserved, including transparent sky");
                }
            }
            // Run the standalone suppression shader against the same real framebuffer.
            String suppressionFragment;
            try(ZipFile jar=new ZipFile(args[1])) {
                suppressionFragment=new String(jar.getInputStream(jar.getEntry("assets/gd656conquest/shaders/program/suppression_world.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            }
            int suppressed=glCreateProgram();glAttachShader(suppressed,shader(GL_VERTEX_SHADER,vertex));
            glAttachShader(suppressed,shader(GL_FRAGMENT_SHADER,suppressionFragment));glLinkProgram(suppressed);
            check(glGetProgrami(suppressed,GL_LINK_STATUS)==GL_TRUE,"Standalone suppression shader links");glUseProgram(suppressed);
            position=glGetAttribLocation(suppressed,"Position");glEnableVertexAttribArray(position);glVertexAttribPointer(position,2,GL_FLOAT,false,0,0L);
            glUniformMatrix4fv(glGetUniformLocation(suppressed,"ProjMat"),false,new float[]{2F/w,0,0,0,0,2,0,0,0,0,-1,0,-1,-1,0,1});
            glUniform2f(glGetUniformLocation(suppressed,"OutSize"),w,1);glUniform1i(glGetUniformLocation(suppressed,"DiffuseSampler"),0);
            for(float opacity:new float[]{0,.25F,.6F}) {
                glUniform1f(glGetUniformLocation(suppressed,"SuppressionOpacity"),opacity);glDrawArrays(GL_TRIANGLES,0,6);
                FloatBuffer pixels=BufferUtils.createFloatBuffer(w*4);glReadPixels(0,0,w,1,GL_RGBA,GL_FLOAT,pixels);
                for(int i=0;i<w;i++) {
                    float x=((i+.5F)/w-.5F)*2;
                    float t=Math.max(0,Math.min(1,(Math.abs(x)-.49F)/.51F));float edge=t*t*(3-2*t);
                    for(int c=0;c<3;c++)check(Math.abs(pixels.get(i*4+c)-colors[i][c]*(1-opacity*edge))<.0002F,"soft edge suppression with unchanged center");
                    check(Math.abs(pixels.get(i*4+3)-(i%3==0?0:i%3==1?.6F:1))<.0001F,"suppression preserves alpha");
                }
            }
            check(glGetError()==GL_NO_ERROR,"No OpenGL errors");
            System.out.println("PASS shipped boundary / suppression GLSL, maximum-combined corners and GPU pixel readback: "+checks+" checks; "+glGetString(GL_RENDERER));
        }finally{glfwDestroyWindow(window);glfwTerminate();}
    }
}
