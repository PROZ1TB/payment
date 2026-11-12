package com.paymentalerts;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A tiny orthographic 3D renderer for the player model.
 * Every body part is a box; each visible face is drawn as a textured (or coloured) parallelogram using
 * the GUI matrix stack, so the figure can rotate and bend exactly like the vanilla sneaking pose.
 */
final class Body3D {
    private Body3D() {}

    private static final float CAM_PITCH = 0.22f;

    private record Face(float ox, float oy, float ux, float uy, float vx, float vy, float depth,
                        int tu, int tv, int rw, int rh, int color) {}

    private static final class Scene {
        final List<Face> faces = new ArrayList<>();
        float cx, feetY, scale;
        float cyaw, syaw, cp, sp;
        float px, py, pz, cr, sr;

        void part(float px, float py, float pz, float rot) {
            this.px = px; this.py = py; this.pz = pz;
            this.cr = Fx.cos(rot);
            this.sr = Fx.sin(rot);
        }

        /** model point -> {screenX, screenY, depth} */
        float[] p(float x, float y, float z) {
            // rotate the part around its joint (positive = top forward / bottom backward)
            float y1 = y * cr + z * sr;
            float z1 = -y * sr + z * cr;
            float X = x + px, Y = y1 + py, Z = z1 + pz;
            // spin around the vertical axis
            float xw = X * cyaw + Z * syaw;
            float zw = -X * syaw + Z * cyaw;
            // camera looks slightly down
            float yc = Y - 12f;
            float yp = yc * cp + zw * sp;
            float zp = -yc * sp + zw * cp;
            return new float[]{cx + xw * scale, feetY + (yp - 12f) * scale, zp};
        }

        void face(float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz,
                  int tu, int tv, int rw, int rh, int color) {
            float[] o = p(ox, oy, oz), u = p(ux, uy, uz), v = p(vx, vy, vz);
            float ax = u[0] - o[0], ay = u[1] - o[1], bx = v[0] - o[0], by = v[1] - o[1];
            float cross = ax * by - ay * bx;
            if (cross <= 0.0001f) return; // facing away
            faces.add(new Face(o[0], o[1], ax, ay, bx, by, (u[2] + v[2]) * 0.5f, tu, tv, rw, rh, color));
        }

        /** Skin-textured box. (u0,v0) = layer origin in the skin, w/h/d = texture size of the box. */
        void tbox(float x0, float y0, float z0, float x1, float y1, float z1, int u0, int v0, int w, int h, int d) {
            face(x0, y0, z1, x1, y0, z1, x0, y1, z1, u0 + d, v0 + d, w, h, 0);                 // front
            face(x1, y0, z0, x0, y0, z0, x1, y1, z0, u0 + 2 * d + w, v0 + d, w, h, 0);         // back
            face(x0, y0, z0, x0, y0, z1, x0, y1, z0, u0, v0 + d, d, h, 0);                     // character's right
            face(x1, y0, z1, x1, y0, z0, x1, y1, z1, u0 + d + w, v0 + d, d, h, 0);             // character's left
            face(x0, y0, z0, x1, y0, z0, x0, y0, z1, u0 + d, v0, w, d, 0);                     // top
        }

        /** Solid-colour box (used for the crown). */
        void cbox(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
            face(x0, y0, z1, x1, y0, z1, x0, y1, z1, -1, -1, 1, 1, shade(rgb, 1.0f));
            face(x1, y0, z0, x0, y0, z0, x1, y1, z0, -1, -1, 1, 1, shade(rgb, 0.6f));
            face(x0, y0, z0, x0, y0, z1, x0, y1, z0, -1, -1, 1, 1, shade(rgb, 0.8f));
            face(x1, y0, z1, x1, y0, z0, x1, y1, z1, -1, -1, 1, 1, shade(rgb, 0.8f));
            face(x0, y0, z0, x1, y0, z0, x0, y0, z1, -1, -1, 1, 1, shade(rgb, 1.15f));
        }
    }

    private static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * @param k   crouch amount 0..1 (vanilla sneak pose: torso leans forward, head drops, legs move back, arms swing back)
     * @param yaw rotation around the vertical axis in radians (0 = facing the viewer)
     * @param crownRgb crown colour (rgb) or -1 for no crown
     */
    static void draw(DrawContext c, Identifier id, boolean slim, float cx, float feetY, float scale,
                     float k, float yaw, int crownRgb) {
        Scene s = new Scene();
        s.cx = cx; s.feetY = feetY; s.scale = scale;
        s.cyaw = Fx.cos(yaw); s.syaw = Fx.sin(yaw);
        s.cp = Fx.cos(CAM_PITCH); s.sp = Fx.sin(CAM_PITCH);
        final float i = 0.3f; // outer layer inflation

        // legs (move back when crouching)
        s.part(-1.9f, 12f + 0.2f * k, -4f * k, 0f);
        s.tbox(-2, 0, -2, 2, 12, 2, 0, 16, 4, 12, 4);
        s.tbox(-2 - i, -i, -2 - i, 2 + i, 12 + i, 2 + i, 0, 32, 4, 12, 4);
        s.part(1.9f, 12f + 0.2f * k, -4f * k, 0f);
        s.tbox(-2, 0, -2, 2, 12, 2, 16, 48, 4, 12, 4);
        s.tbox(-2 - i, -i, -2 - i, 2 + i, 12 + i, 2 + i, 0, 48, 4, 12, 4);

        // torso (leans forward)
        s.part(0f, 3.2f * k, 0f, 0.5f * k);
        s.tbox(-4, 0, -2, 4, 12, 2, 16, 16, 8, 12, 4);
        s.tbox(-4 - i, -i, -2 - i, 4 + i, 12 + i, 2 + i, 16, 32, 8, 12, 4);

        // arms (swing back a little)
        int aw = slim ? 3 : 4;
        float rl = slim ? -2f : -3f;
        float lr = slim ? 2f : 3f;
        s.part(-5f, 2f + 3.2f * k, 0f, 0.4f * k);
        s.tbox(rl, -2, -2, 1, 10, 2, 40, 16, aw, 12, 4);
        s.tbox(rl - i, -2 - i, -2 - i, 1 + i, 10 + i, 2 + i, 40, 32, aw, 12, 4);
        s.part(5f, 2f + 3.2f * k, 0f, 0.4f * k);
        s.tbox(-1, -2, -2, lr, 10, 2, 32, 48, aw, 12, 4);
        s.tbox(-1 - i, -2 - i, -2 - i, lr + i, 10 + i, 2 + i, 48, 48, aw, 12, 4);

        // head (drops forward of the torso)
        s.part(0f, 4.2f * k, 0f, 0f);
        s.tbox(-4, -8, -4, 4, 0, 4, 0, 0, 8, 8, 8);
        s.tbox(-4.5f, -8.5f, -4.5f, 4.5f, 0.5f, 4.5f, 32, 0, 8, 8, 8);

        // 3D crown: four walls, eight spikes, three jewels
        if (crownRgb >= 0) {
            float y0 = -10.8f, y1 = -8.0f;
            s.cbox(-5.2f, y0, 4.4f, 5.2f, y1, 5.2f, crownRgb);
            s.cbox(-5.2f, y0, -5.2f, 5.2f, y1, -4.4f, crownRgb);
            s.cbox(4.4f, y0, -4.4f, 5.2f, y1, 4.4f, crownRgb);
            s.cbox(-5.2f, y0, -4.4f, -4.4f, y1, 4.4f, crownRgb);
            float[][] sp = {{-4.6f, -4.6f}, {0f, -4.8f}, {4.6f, -4.6f}, {-4.8f, 0f}, {4.8f, 0f}, {-4.6f, 4.6f}, {0f, 4.8f}, {4.6f, 4.6f}};
            for (float[] q : sp) {
                s.cbox(q[0] - 0.9f, y0 - 3.2f, q[1] - 0.9f, q[0] + 0.9f, y0, q[1] + 0.9f, crownRgb);
                s.cbox(q[0] - 0.45f, y0 - 4.2f, q[1] - 0.45f, q[0] + 0.45f, y0 - 3.2f, q[1] + 0.45f, 0xFFFFFF);
            }
            int[] jewels = {0xFF4D4D, 0x4DA3FF, 0x6BFF8E};
            for (int j = 0; j < 3; j++) {
                float jx = -2.4f + j * 2.4f;
                s.cbox(jx - 0.6f, -9.9f, 5.2f, jx + 0.6f, -8.7f, 5.7f, jewels[j]);
            }
        }

        s.faces.sort(Comparator.comparingDouble(Face::depth));

        var m = c.getMatrices();
        for (Face f : s.faces) {
            m.pushMatrix();
            if (f.tu() >= 0) {
                m.mul(new Matrix3x2f(f.ux() / f.rw(), f.uy() / f.rw(), f.vx() / f.rh(), f.vy() / f.rh(), f.ox(), f.oy()));
                c.drawTexture(RenderPipelines.GUI_TEXTURED, id, 0, 0, (float) f.tu(), (float) f.tv(), f.rw(), f.rh(), 64, 64);
            } else {
                m.mul(new Matrix3x2f(f.ux(), f.uy(), f.vx(), f.vy(), f.ox(), f.oy()));
                c.fill(0, 0, 1, 1, f.color());
            }
            m.popMatrix();
        }
    }
}
