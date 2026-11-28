import c_y.meknh_core.client.structure.StructurePreviewTransform;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Isolated synthetic CCW cube GPU regression; NOT a Minecraft screenshot/test. */
public final class StructureCullingGpuProbe {
    private static final int SIZE = 240;
    private static final int[] COLORS = {0xe74747, 0x43bb70, 0x4085e5, 0xefb43d, 0xbb65d4, 0x35bcc4};
    // Each quad has an outward normal; verify this independently before GPU work.
    private static final float[][][] FACES = {
        {{1,-1,-1},{1,1,-1},{1,1,1},{1,-1,1}},
        {{-1,-1,1},{-1,1,1},{-1,1,-1},{-1,-1,-1}},
        {{-1,1,-1},{-1,1,1},{1,1,1},{1,1,-1}},
        {{-1,-1,1},{-1,-1,-1},{1,-1,-1},{1,-1,1}},
        {{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}},
        {{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}}
    };
    private record Frame(BufferedImage image, float[] depth, int[] color) {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/structure-culling-gpu" : args[0]);
        Files.createDirectories(output);
        for (float[][] face : FACES) {
            Vector3f a = new Vector3f(face[0]), b = new Vector3f(face[1]), c = new Vector3f(face[2]);
            if (b.sub(a).cross(c.sub(a)).dot(a) <= 0) throw new AssertionError("Cube is not outward CCW");
        }
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed; GPU test NOT run");
        long window = 0;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
            window = glfwCreateWindow(SIZE, SIZE, "Synthetic culling regression (hidden)", 0, 0);
            if (window == 0) throw new IllegalStateException("Hidden GLFW context unavailable");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            System.out.println("GPU: " + glGetString(GL_VENDOR) + " / " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
            if (!GL.getCapabilities().OpenGL30) throw new IllegalStateException("OpenGL 3.0 FBO required");
            int fbo = glGenFramebuffers(), color = glGenRenderbuffers(), depth = glGenRenderbuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glBindRenderbuffer(GL_RENDERBUFFER, color);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, SIZE, SIZE);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, color);
            glBindRenderbuffer(GL_RENDERBUFFER, depth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, SIZE, SIZE);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete FBO");
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            glViewport(0, 0, SIZE, SIZE);
            glDisable(GL_DITHER);
            glDisable(GL_BLEND);
            glDisable(GL_LIGHTING);
            glDisable(GL_MULTISAMPLE);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glFrontFace(GL_CCW);
            glCullFace(GL_BACK);
            glMatrixMode(GL_PROJECTION);
            FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
            glLoadMatrixf(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, -1000, 1000).get(matrixBuffer));
            float[][] angles = {{0,0},{25,135},{-35,40},{60,225},{-70,310},{10,180}};
            BufferedImage comparison = new BufferedImage(SIZE * 4, (SIZE + 38) * angles.length + 38, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = comparison.createGraphics();
            g.setColor(new Color(0x171b24)); g.fillRect(0,0,comparison.getWidth(),comparison.getHeight());
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); g.setColor(Color.WHITE);
            g.drawString("SYNTHETIC GPU CUBE / NOT MINECRAFT / no-cull depth oracle selects nearest exterior", 8, 23);
            int failures = 0;
            for (int row = 0; row < angles.length; row++) {
                float pitch = angles[row][0], yaw = angles[row][1];
                Matrix4f legacy = new Matrix4f().translate(SIZE/2f,SIZE/2f,0).scale(52,-52,-52)
                    .rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
                Matrix4f production = StructurePreviewTransform.apply(new Matrix4f().translate(SIZE/2f,SIZE/2f,0),52,pitch,yaw);
                Frame old = render(legacy,true), oldOracle = render(legacy,false);
                Frame actual = render(production,true), oracle = render(production,false);
                int oldWrong = mismatch(old, oldOracle), wrong = mismatch(actual, oracle);
                int oldFar = farther(old, oldOracle);
                int pixels = occupied(oracle);
                int oldBackfaces = backfaces(old, legacy), backfaces = backfaces(actual, production);
                System.out.printf("pitch=%s yaw=%s exteriorPixels=%d legacyWrong=%d legacyFarther=%d productionWrong=%d%n", pitch,yaw,pixels,oldWrong,oldFar,wrong);
                System.out.printf("  outward-normal check: legacyBackfaces=%d productionBackfaces=%d%n", oldBackfaces, backfaces);
                if (pixels < 5000 || oldWrong < 1000 || oldFar < 1000 || oldBackfaces < 1000 || wrong != 0 || backfaces != 0) failures++;
                int y = 38 + row*(SIZE+38);
                Frame[] frames = {old,oldOracle,oracle,actual};
                String[] labels = {"LEGACY: wrong backfaces", "LEGACY nearest exterior", "FIXED nearest exterior", "PRODUCTION: culled"};
                for(int col=0;col<4;col++) {
                    g.setColor(Color.WHITE); g.drawString(labels[col],col*SIZE+4,y+12);
                    g.drawString("pitch="+pitch+" yaw="+yaw,col*SIZE+4,y+24);
                    g.drawImage(frames[col].image,col*SIZE,y+25,null);
                }
            }
            g.dispose();
            Path png = output.resolve("culling-comparison.png");
            ImageIO.write(comparison,"png",png.toFile());
            System.out.println("Synthetic visualization: " + png.toAbsolutePath());
            glDeleteRenderbuffers(color); glDeleteRenderbuffers(depth); glDeleteFramebuffers(fbo);
            if (failures != 0) throw new AssertionError("Exterior-surface GPU regression FAILED in " + failures + " orientations (production must match nearest-depth no-cull oracle)");
            System.out.println("PASS: 6 GPU orientations; old Z reflection reproduced far/backface surfaces; production exposes nearest exterior with CCW + BACK culling + depth test.");
        } finally {
            if(window != 0) { GL.setCapabilities(null); glfwDestroyWindow(window); }
            glfwTerminate(); errors.free();
        }
    }
    private static Frame render(Matrix4f model, boolean cull) {
        if(cull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        glClearColor(0.07f,0.08f,0.11f,1); glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(model.get(BufferUtils.createFloatBuffer(16)));
        glBegin(GL_QUADS);
        for(int f=0;f<FACES.length;f++) {
            int color=COLORS[f]; glColor3ub((byte)(color>>16),(byte)(color>>8),(byte)color);
            for(float[] v:FACES[f]) glVertex3f(v[0],v[1],v[2]);
        }
        glEnd(); glFinish();
        ByteBuffer rgba=BufferUtils.createByteBuffer(SIZE*SIZE*4);
        FloatBuffer depths=BufferUtils.createFloatBuffer(SIZE*SIZE);
        glReadPixels(0,0,SIZE,SIZE,GL_RGBA,GL_UNSIGNED_BYTE,rgba);
        glReadPixels(0,0,SIZE,SIZE,GL_DEPTH_COMPONENT,GL_FLOAT,depths);
        int error=glGetError(); if(error!=GL_NO_ERROR) throw new AssertionError("GL error " + error);
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_RGB);
        int[] colors=new int[SIZE*SIZE]; float[] z=new float[SIZE*SIZE]; depths.get(z);
        for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
            int i=y*SIZE+x,k=i*4;
            colors[i]=((rgba.get(k)&255)<<16)|((rgba.get(k+1)&255)<<8)|(rgba.get(k+2)&255);
            image.setRGB(x,SIZE-1-y,colors[i]);
        }
        return new Frame(image,z,colors);
    }
    // Independent geometric test: physical outward normals must point toward +view Z.
    // Inverse-transpose is deliberate: do not derive orientation from projected winding.
    private static int backfaces(Frame frame, Matrix4f model) {
        Matrix3f normalMatrix = model.normal(new Matrix3f());
        boolean[] back = new boolean[FACES.length];
        for (int f=0; f<FACES.length; f++) {
            Vector3f a=new Vector3f(FACES[f][0]);
            Vector3f n=new Vector3f(FACES[f][1]).sub(a).cross(new Vector3f(FACES[f][2]).sub(a));
            back[f]=normalMatrix.transform(n).z <= 0;
        }
        int count=0;
        for(int i=0;i<frame.color.length;i++) if(frame.depth[i]<1)
            for(int f=0;f<COLORS.length;f++) if(frame.color[i]==COLORS[f] && back[f]) count++;
        return count;
    }
    private static int occupied(Frame frame) { int n=0; for(float d:frame.depth) if(d<1) n++; return n; }
    private static int mismatch(Frame frame,Frame oracle) {
        int n=0;
        for(int i=0;i<frame.depth.length;i++) if(frame.color[i]!=oracle.color[i] || Math.abs(frame.depth[i]-oracle.depth[i])>0.000002f) n++;
        return n;
    }
    private static int farther(Frame frame,Frame oracle) {
        int n=0; for(int i=0;i<frame.depth.length;i++) if(oracle.depth[i]<1 && frame.depth[i]>oracle.depth[i]+0.00001f) n++; return n;
    }
}
