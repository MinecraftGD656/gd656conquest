package tests;

import java.nio.*;
import java.nio.file.*;
import java.util.zip.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Compiles the actual shipped GLSL and reads back pixels from a hidden OpenGL context. */
public final class DeathFilterGpuTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static int shader(int kind,String source){
        int id=glCreateShader(kind);glShaderSource(id,source);glCompileShader(id);
        check(glGetShaderi(id,GL_COMPILE_STATUS)==GL_TRUE,"GLSL compilation: "+glGetShaderInfoLog(id));return id;
    }
    static String packagedFragment(String jar)throws Exception {
        try(ZipFile zip=new ZipFile(jar)) {
            return new String(zip.getInputStream(zip.getEntry("assets/gd656conquest/shaders/program/death_world.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
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
            glAttachShader(program,shader(GL_FRAGMENT_SHADER,packagedFragment(args[1])));
            glLinkProgram(program);check(glGetProgrami(program,GL_LINK_STATUS)==GL_TRUE,"Program linking: "+glGetProgramInfoLog(program));glUseProgram(program);
            float[][] colors={{1,0,0},{0,1,0},{0,0,1},{1,1,1},{0,0,0},{.5F,.5F,.5F},{.01F,.02F,.03F},{.95F,.85F,.75F},{.3F,.5F,.7F}};
            int w=colors.length;
            FloatBuffer source=BufferUtils.createFloatBuffer(w*4);
            for(float[] c:colors)source.put(c).put(.6F);source.flip();
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
            for(float contrast:new float[]{1,1.03F})for(float strength:new float[]{0,.48F,.96F,1}){
                glUniform1f(glGetUniformLocation(program,"DeathFilterStrength"),strength);glUniform1f(glGetUniformLocation(program,"DeathContrast"),contrast);
                glDrawArrays(GL_TRIANGLES,0,6);FloatBuffer pixels=BufferUtils.createFloatBuffer(w*4);glReadPixels(0,0,w,1,GL_RGBA,GL_FLOAT,pixels);
                for(int i=0;i<w;i++){
                    float[] c=colors[i];float lum=.299F*c[0]+.587F*c[1]+.114F*c[2];
                    float adjustment=(contrast-1)*(lum-.5F)*4*lum*(1-lum)*strength;
                    for(int channel=0;channel<3;channel++){
                        float expected=Math.max(0,Math.min(1,c[channel]*(1-strength)+lum*strength+adjustment));
                        check(Math.abs(pixels.get(i*4+channel)-expected)<.0001F,"GPU color math matches strength/contrast for sample "+i);
                    }
                    check(Math.abs(pixels.get(i*4+3)-.6F)<.0001F,"Source alpha preserved");
                    if(contrast==1){float actualLum=.299F*pixels.get(i*4)+.587F*pixels.get(i*4+1)+.114F*pixels.get(i*4+2);
                        check(Math.abs(actualLum-lum)<.0001F,"Desaturation preserves luminance");}
                }
            }
            check(glGetError()==GL_NO_ERROR,"No OpenGL errors");
            System.out.println("PASS actual GLSL compilation and GPU pixel readback: "+checks+" checks; "+glGetString(GL_RENDERER));
        }finally{glfwDestroyWindow(window);glfwTerminate();}
    }
}
