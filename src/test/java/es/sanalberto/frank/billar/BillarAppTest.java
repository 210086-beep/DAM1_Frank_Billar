package es.sanalberto.frank.billar;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BillarAppTest {

    @Test
    @DisplayName("El impulso elástico transfiere prácticamente toda la velocidad en choque frontal")
    void testElasticCollisionTransfer() {
        float massA = 0.5089f;
        float massB = 0.5089f;
        float invMassA = 1.0f / massA;
        float invMassB = 1.0f / massB;
        float totalInvMass = invMassA + invMassB;

        Vec2 normal = new Vec2(1.0f, 0.0f);
        Vec2 vA = new Vec2(2.0f, 0.0f);
        Vec2 vB = new Vec2(0.0f, 0.0f);
        Vec2 vRel = vB.sub(vA);

        float vNorm = Vec2.dot(vRel, normal); // -2.0
        float restitution = 0.98f;

        float jN = -(1.0f + restitution) * vNorm / totalInvMass;
        Vec2 impulseN = new Vec2(normal.x * jN, normal.y * jN);

        Vec2 vA_post = vA.sub(impulseN.mul(invMassA));
        Vec2 vB_post = vB.add(impulseN.mul(invMassB));

        // En un choque elástico perfecto con masas idénticas, A se detiene casi por completo (restitución 0.98)
        assertEquals(0.02f, vA_post.x, 0.001f);
        assertEquals(1.98f, vB_post.x, 0.001f);
    }

    @Test
    @DisplayName("El cálculo de dirección de tiro normaliza el vector correctamente")
    void testAimDirectionNormalization() {
        float ballX = 0.55f;
        float ballY = 0.60f;
        float mouseX = 1.55f;
        float mouseY = 1.60f;

        float dx = mouseX - ballX;
        float dy = mouseY - ballY;
        float len = (float) Math.hypot(dx, dy);

        Vec2 dir = new Vec2(dx / len, dy / len);

        assertEquals(1.0f, dir.length(), 0.0001f);
        assertEquals(Math.sqrt(0.5), dir.x, 0.001f);
        assertEquals(Math.sqrt(0.5), dir.y, 0.001f);
    }

    @Test
    @DisplayName("Verificación de detección de troneras")
    void testPocketDetection() {
        Vec2 pocket = new Vec2(0.09f, 0.09f);
        float pocketRadius = 0.045f * 1.95f;

        Vec2 ballIn = new Vec2(0.10f, 0.10f);
        Vec2 ballOut = new Vec2(0.30f, 0.30f);

        float distInSq = (ballIn.x - pocket.x) * (ballIn.x - pocket.x) + (ballIn.y - pocket.y) * (ballIn.y - pocket.y);
        float distOutSq = (ballOut.x - pocket.x) * (ballOut.x - pocket.x) + (ballOut.y - pocket.y) * (ballOut.y - pocket.y);

        assertTrue(distInSq <= pocketRadius * pocketRadius);
        assertFalse(distOutSq <= pocketRadius * pocketRadius);
    }
}
