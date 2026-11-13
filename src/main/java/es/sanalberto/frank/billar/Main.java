package es.sanalberto.frank.billar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.contacts.Contact;

public class Main {

    public static void main(String[] args) {
        Application.launch(BillarApp.class, args);
    }

    public static class BillarApp extends Application {

        // Escala: 1 metro = 300 píxeles
        private static final double SCALE = 300.0;

        // Margen visual de la mesa
        private static final double OFFSET_X = 40.0;
        private static final double OFFSET_Y = 40.0;

        // Dimensiones mesa en metros (reglamentaria 8 pies)
        private static final double TABLE_W = 2.4;
        private static final double TABLE_H = 1.2;

        // Radio de bola en metros
        private static final double BALL_R = 0.045;

        // Paso de física fijo (120 Hz para máxima precisión en choques)
        private static final double PHYSICS_STEP = 1.0 / 120.0;

        // Coeficiente de restitución (elasticidad casi perfecta entre bolas de resina fenólica)
        private static final float BALL_BALL_RESTITUTION = 0.98f;

        // Fricción entre bolas durante el choque (cut-induced throw)
        private static final float BALL_BALL_FRICTION = 0.04f;

        // Deceleración constante por resistencia de rodadura sobre el paño (m/s^2)
        private static final float ROLLING_RESISTANCE = 0.22f;

        // Potencia máxima del golpe
        private static final float MAX_IMPULSE = 6.2f;

        // Radio de detección de troneras
        private static final float POCKET_RADIUS = (float) (BALL_R * 1.95);

        private World world;
        private Body bolaBlanca;
        private final List<Ball> balls = new ArrayList<>();
        private final List<ImpactEffect> impactEffects = new ArrayList<>();

        private Canvas canvas;

        private float mouseX = 0f;
        private float mouseY = 0f;

        private long lastFrameTime = -1;
        private double accumulator = 0.0;

        private boolean charging = false;
        private float power = 0f;
        private double dragStartScreenY = 0;

        private Vec2 aimDir = new Vec2(1, 0);
        private Vec2[] pockets;

        // Control de reinicio automático
        private double restartTimer = -1.0;
        private static final double RESTART_DELAY = 2.5;

        @Override
        public void start(Stage stage) {
            canvas = new Canvas(
                    TABLE_W * SCALE + OFFSET_X * 2 + 80,
                    TABLE_H * SCALE + OFFSET_Y * 2
            );

            setupMouse();
            initPhysics();

            AnimationTimer loop = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (lastFrameTime < 0) {
                        lastFrameTime = now;
                        return;
                    }

                    double elapsed = (now - lastFrameTime) / 1_000_000_000.0;
                    lastFrameTime = now;

                    if (elapsed > 0.05) {
                        elapsed = 0.05;
                    }

                    accumulator += elapsed;

                    while (accumulator >= PHYSICS_STEP) {
                        world.step((float) PHYSICS_STEP, 10, 8);
                        applyRealisticRollingResistance();
                        separateOverlaps();
                        handlePockets();
                        checkAutoRestart(PHYSICS_STEP);
                        accumulator -= PHYSICS_STEP;
                    }

                    updateImpactEffects(elapsed);
                    draw();
                }
            };

            loop.start();

            StackPane root = new StackPane(canvas);
            Scene scene = new Scene(root);

            // Tecla 'R' para reiniciar la partida en cualquier momento
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.R) {
                    resetGame();
                }
            });

            stage.setTitle("Billar - Superbolado (DAM 1)");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
        }

        private void setupMouse() {
            canvas.setOnMousePressed(this::press);
            canvas.setOnMouseDragged(this::drag);
            canvas.setOnMouseReleased(this::release);
            canvas.setOnMouseMoved(this::move);
            canvas.setOnMouseExited(e -> {
                charging = false;
                power = 0f;
            });
        }

        private void move(MouseEvent e) {
            updateMouse(e);
        }

        private void press(MouseEvent e) {
            if (ballsMoving() || restartTimer > 0) return;

            updateMouse(e);

            charging = true;
            dragStartScreenY = e.getY();
            power = 0f;

            aimDir = computeAimDirection();
        }

        private void drag(MouseEvent e) {
            updateMouse(e);

            if (!charging) return;

            double dy = e.getY() - dragStartScreenY;
            power = (float) Math.max(0.0, Math.min(dy / 180.0, 1.0));
        }

        private void release(MouseEvent e) {
            updateMouse(e);

            if (!charging) return;

            charging = false;

            if (ballsMoving() || restartTimer > 0) {
                power = 0f;
                return;
            }

            if (power > 0.03f && bolaBlanca != null) {
                Vec2 impulse = new Vec2(
                        aimDir.x * power * MAX_IMPULSE,
                        aimDir.y * power * MAX_IMPULSE
                );

                bolaBlanca.setAwake(true);
                bolaBlanca.applyLinearImpulse(impulse, bolaBlanca.getWorldCenter());
                SoundEffects.playBallCollision(power);
            }

            power = 0f;
        }

        private void updateMouse(MouseEvent e) {
            mouseX = (float) ((e.getX() - OFFSET_X) / SCALE);
            mouseY = (float) ((e.getY() - OFFSET_Y) / SCALE);
        }

        private Vec2 computeAimDirection() {
            if (bolaBlanca == null) return new Vec2(1, 0);

            Vec2 pos = bolaBlanca.getPosition();

            float dx = mouseX - pos.x;
            float dy = mouseY - pos.y;

            float len = (float) Math.hypot(dx, dy);

            if (len < 0.001f) {
                return new Vec2(1, 0);
            }

            return new Vec2(dx / len, dy / len);
        }

        private boolean ballsMoving() {
            for (Ball ball : balls) {
                Vec2 v = ball.body.getLinearVelocity();
                if (v.lengthSquared() > 0.0002f) {
                    return true;
                }
            }
            return false;
        }

        private void initPhysics() {
            world = new World(new Vec2(0, 0));

            setupCollisionListener();

            float cornerInset = 0.09f;
            float sidePocketY = 0.02f;

            pockets = new Vec2[]{
                    new Vec2(cornerInset, cornerInset),
                    new Vec2((float) TABLE_W / 2f, sidePocketY),
                    new Vec2((float) TABLE_W - cornerInset, cornerInset),

                    new Vec2(cornerInset, (float) TABLE_H - cornerInset),
                    new Vec2((float) TABLE_W / 2f, (float) TABLE_H - sidePocketY),
                    new Vec2((float) TABLE_W - cornerInset, (float) TABLE_H - cornerInset)
            };

            createWalls();
            createBalls();
        }

        /**
         * Configura el listener de contacto para resolver choques realistas:
         * 1) Choque bola-bola: desactivamos el solver por defecto de Box2D (que anula
         * la restitución a velocidades bajas) y aplicamos impulso físico elástico exacto
         * con restitución fenólica (0.98), fricción tangencial y transferencia de giro.
         * 2) Choque bola-banda: restitución de banda de goma (0.74) y fricción del paño (0.24).
         */
        private void setupCollisionListener() {
            world.setContactListener(new ContactListener() {
                @Override
                public void beginContact(Contact contact) {
                }

                @Override
                public void endContact(Contact contact) {
                }

                @Override
                public void preSolve(Contact contact, Manifold oldManifold) {
                    Fixture fA = contact.getFixtureA();
                    Fixture fB = contact.getFixtureB();
                    Body bA = fA.getBody();
                    Body bB = fB.getBody();

                    boolean aIsBall = bA.getUserData() instanceof Ball;
                    boolean bIsBall = bB.getUserData() instanceof Ball;

                    if (aIsBall && bIsBall) {
                        // Desactivamos el solver estándar para este contacto entre bolas
                        contact.setEnabled(false);

                        Vec2 posA = bA.getPosition();
                        Vec2 posB = bB.getPosition();
                        Vec2 delta = posB.sub(posA);
                        float dist = delta.length();
                        if (dist < 1e-5f) return;

                        Vec2 normal = new Vec2(delta.x / dist, delta.y / dist);
                        Vec2 vA = bA.getLinearVelocity();
                        Vec2 vB = bB.getLinearVelocity();
                        Vec2 vRel = vB.sub(vA);

                        float vNorm = Vec2.dot(vRel, normal);
                        if (vNorm >= 0f) return; // Se están separando

                        float invMassA = 1.0f / bA.getMass();
                        float invMassB = 1.0f / bB.getMass();
                        float totalInvMass = invMassA + invMassB;
                        if (totalInvMass <= 0f) return;

                        // Impulso normal con restitución exacta
                        float jN = -(1.0f + BALL_BALL_RESTITUTION) * vNorm / totalInvMass;
                        Vec2 impulseN = new Vec2(normal.x * jN, normal.y * jN);

                        // Impulso tangencial por fricción (cut-induced throw y transferencia de spin)
                        Vec2 tangent = new Vec2(-normal.y, normal.x);
                        float wA = bA.getAngularVelocity();
                        float wB = bB.getAngularVelocity();
                        float vTang = Vec2.dot(vRel, tangent) + wA * (float) BALL_R + wB * (float) BALL_R;

                        float jT = -vTang / totalInvMass;
                        float maxJT = BALL_BALL_FRICTION * jN;
                        if (jT > maxJT) jT = maxJT;
                        else if (jT < -maxJT) jT = -maxJT;

                        Vec2 impulseT = new Vec2(tangent.x * jT, tangent.y * jT);
                        Vec2 totalImpulse = impulseN.add(impulseT);

                        bA.setLinearVelocity(vA.sub(totalImpulse.mul(invMassA)));
                        bB.setLinearVelocity(vB.add(totalImpulse.mul(invMassB)));

                        // Momento de inercia esfera maciza: I = 2/5 * m * r^2
                        float iInvA = 1.0f / (0.4f * bA.getMass() * (float) (BALL_R * BALL_R));
                        float iInvB = 1.0f / (0.4f * bB.getMass() * (float) (BALL_R * BALL_R));
                        bA.setAngularVelocity(wA - jT * (float) BALL_R * iInvA);
                        bB.setAngularVelocity(wB - jT * (float) BALL_R * iInvB);

                        // Efecto sonoro y visual del choque
                        float impactSpeed = -vNorm;
                        if (impactSpeed > 0.04f) {
                            SoundEffects.playBallCollision(impactSpeed / 3.5f);
                            double midX = OFFSET_X + (posA.x + posB.x) * 0.5 * SCALE;
                            double midY = OFFSET_Y + (posA.y + posB.y) * 0.5 * SCALE;
                            addImpactEffect(midX, midY, impactSpeed);
                        }
                    } else if (aIsBall || bIsBall) {
                        // Choque con la banda: goma con absorción y paño con fricción
                        contact.setRestitution(0.74f);
                        contact.setFriction(0.24f);
                    }
                }

                @Override
                public void postSolve(Contact contact, ContactImpulse impulse) {
                }
            });
        }

        private void createWalls() {
            float thickness = 0.12f;

            float hw = (float) TABLE_W / 2f;
            float hh = (float) TABLE_H / 2f;

            // Arriba
            createStaticBox(hw, -thickness / 2f, hw + thickness, thickness / 2f);
            // Abajo
            createStaticBox(hw, (float) TABLE_H + thickness / 2f, hw + thickness, thickness / 2f);
            // Izquierda
            createStaticBox(-thickness / 2f, hh, thickness / 2f, hh + thickness);
            // Derecha
            createStaticBox((float) TABLE_W + thickness / 2f, hh, thickness / 2f, hh + thickness);
        }

        private void createStaticBox(float cx, float cy, float halfWidth, float halfHeight) {
            BodyDef def = new BodyDef();
            def.type = BodyType.STATIC;
            def.position.x = cx;
            def.position.y = cy;

            Body body = world.createBody(def);

            PolygonShape shape = new PolygonShape();
            shape.setAsBox(halfWidth, halfHeight);

            FixtureDef fixture = new FixtureDef();
            fixture.shape = shape;
            fixture.friction = 0.22f;
            fixture.restitution = 0.74f;

            body.createFixture(fixture);
            body.setUserData("WALL");
        }

        /**
         * Crea el conjunto reglamentario de bolas:
         * Bola blanca en cabecera y triángulo de 15 bolas perfectamente empaquetadas
         * (sin holguras artificiales) con la bola 8 en el centro de la 3ª fila.
         */
        private void createBalls() {
            bolaBlanca = createBall(0.55f, (float) TABLE_H / 2f, Color.WHITE, true, 0);

            // Colores reglamentarios de bolas de billar pool
            Color[] ballColors = {
                    Color.web("#F1C40F"), // 1 Amarilla
                    Color.web("#2980B9"), // 2 Azul
                    Color.web("#C0392B"), // 3 Roja
                    Color.web("#8E44AD"), // 4 Morada
                    Color.web("#E67E22"), // 5 Naranja
                    Color.web("#27AE60"), // 6 Verde
                    Color.web("#78281F"), // 7 Granate
                    Color.web("#17202A"), // 8 Negra
                    Color.web("#F39C12"), // 9 Rayada Amarilla
                    Color.web("#3498DB"), // 10 Rayada Azul
                    Color.web("#E74C3C"), // 11 Rayada Roja
                    Color.web("#9B59B6"), // 12 Rayada Morada
                    Color.web("#D35400"), // 13 Rayada Naranja
                    Color.web("#2ECC71"), // 14 Rayada Verde
                    Color.web("#922B21")  // 15 Rayada Granate
            };

            // Disposición del triángulo oficial (8 en el centro, esquinas mixtas)
            int[][] rackSetup = {
                    {1},
                    {2, 9},
                    {3, 8, 10},
                    {11, 4, 12, 5},
                    {13, 6, 14, 7, 15}
            };

            float startX = 1.65f;
            // Distancia entre centros para que las bolas se toquen de verdad en el triángulo
            float spacing = (float) (BALL_R * 2.0015);

            for (int row = 0; row < rackSetup.length; row++) {
                int[] ballsInRow = rackSetup[row];
                for (int i = 0; i < ballsInRow.length; i++) {
                    int ballNum = ballsInRow[i];
                    float x = (float) (startX + row * spacing * Math.sqrt(3.0) / 2.0);
                    float y = (float) (TABLE_H / 2.0 + (i - row / 2.0) * spacing);

                    createBall(x, y, ballColors[ballNum - 1], false, ballNum);
                }
            }
        }

        private Body createBall(float x, float y, Color color, boolean cue, int number) {
            BodyDef def = new BodyDef();
            def.type = BodyType.DYNAMIC;
            def.position.x = x;
            def.position.y = y;

            Body body = world.createBody(def);
            body.setBullet(true);

            CircleShape shape = new CircleShape();
            shape.m_radius = (float) BALL_R;

            FixtureDef fixture = new FixtureDef();
            fixture.shape = shape;
            fixture.density = 80f;
            fixture.friction = BALL_BALL_FRICTION;
            fixture.restitution = BALL_BALL_RESTITUTION;

            body.createFixture(fixture);

            // Damping suave; la deceleración física principal la aplica ROLLING_RESISTANCE
            body.setLinearDamping(0.18f);
            body.setAngularDamping(0.40f);

            Ball ball = new Ball(body, color, cue, number);
            body.setUserData(ball);
            balls.add(ball);

            return body;
        }

        /**
         * Aplica la deceleración constante por resistencia de rodadura sobre el paño
         * de billar y detiene las bolas limpiamente sin deslizamientos residuales infinitos.
         */
        private void applyRealisticRollingResistance() {
            float decel = ROLLING_RESISTANCE * (float) PHYSICS_STEP;

            for (Ball ball : balls) {
                Vec2 v = ball.body.getLinearVelocity();
                float speed = v.length();

                if (speed > 0.0001f) {
                    if (speed <= decel + 0.015f) {
                        ball.body.setLinearVelocity(new Vec2(0f, 0f));
                        ball.body.setAngularVelocity(0f);
                    } else {
                        float factor = (speed - decel) / speed;
                        ball.body.setLinearVelocity(new Vec2(v.x * factor, v.y * factor));
                        ball.body.setAngularVelocity(ball.body.getAngularVelocity() * 0.985f);
                    }
                }
            }
        }

        /**
         * Separación posicional para evitar que las bolas penetren o se superpongan
         * incluso tras saques de máxima potencia.
         */
        private void separateOverlaps() {
            float minDist = (float) (BALL_R * 2.0);
            float minDistSq = minDist * minDist;

            for (int i = 0; i < balls.size(); i++) {
                Ball b1 = balls.get(i);
                Vec2 p1 = b1.body.getPosition();

                for (int j = i + 1; j < balls.size(); j++) {
                    Ball b2 = balls.get(j);
                    Vec2 p2 = b2.body.getPosition();

                    float dx = p2.x - p1.x;
                    float dy = p2.y - p1.y;
                    float d2 = dx * dx + dy * dy;

                    if (d2 < minDistSq && d2 > 1e-8f) {
                        float d = (float) Math.sqrt(d2);
                        float overlap = 0.5f * (minDist - d);
                        float nx = dx / d;
                        float ny = dy / d;

                        b1.body.setTransform(new Vec2(p1.x - nx * overlap, p1.y - ny * overlap), b1.body.getAngle());
                        b2.body.setTransform(new Vec2(p2.x + nx * overlap, p2.y + ny * overlap), b2.body.getAngle());
                        p1 = b1.body.getPosition();
                    }
                }
            }
        }

        private void handlePockets() {
            Iterator<Ball> iterator = balls.iterator();

            while (iterator.hasNext()) {
                Ball ball = iterator.next();
                Vec2 p = ball.body.getPosition();

                boolean pocketed = false;

                for (Vec2 pocket : pockets) {
                    float dx = p.x - pocket.x;
                    float dy = p.y - pocket.y;
                    float distSq = dx * dx + dy * dy;

                    // Efecto embudo: ligera atracción gravitatoria al acercarse al borde de la tronera
                    if (distSq < POCKET_RADIUS * POCKET_RADIUS * 1.4f && distSq > 1e-6f) {
                        float dist = (float) Math.sqrt(distSq);
                        float pull = 0.65f * (1.0f - dist / (POCKET_RADIUS * 1.2f));
                        if (pull > 0f) {
                            ball.body.applyForceToCenter(new Vec2(-dx / dist * pull, -dy / dist * pull));
                        }
                    }

                    if (distSq <= POCKET_RADIUS * POCKET_RADIUS * 0.85f) {
                        pocketed = true;
                        break;
                    }
                }

                if (!pocketed) continue;

                if (ball.cue) {
                    // Falta: la bola blanca vuelve a la línea de cabecera
                    Vec2 respawn = new Vec2(0.55f, (float) TABLE_H / 2f);
                    for (Ball other : balls) {
                        if (!other.cue && other.body.getPosition().sub(respawn).length() < BALL_R * 2.2f) {
                            respawn.x -= 0.15f;
                            break;
                        }
                    }
                    ball.body.setTransform(respawn, 0f);
                    ball.body.setLinearVelocity(new Vec2(0f, 0f));
                    ball.body.setAngularVelocity(0f);
                    ball.body.setAwake(true);
                    SoundEffects.playPocket();
                } else {
                    world.destroyBody(ball.body);
                    iterator.remove();
                    SoundEffects.playPocket();
                }
            }
        }

        /**
         * Comprueba si se han metido todas las bolas numeradas e inicia la cuenta atrás
         * para reiniciar automáticamente el juego.
         */
        private void checkAutoRestart(double dt) {
            int objectBallsLeft = countObjectBalls();

            if (objectBallsLeft == 0) {
                if (restartTimer < 0) {
                    restartTimer = RESTART_DELAY;
                } else {
                    restartTimer -= dt;
                    if (restartTimer <= 0) {
                        resetGame();
                    }
                }
            } else {
                restartTimer = -1.0;
            }
        }

        private int countObjectBalls() {
            int count = 0;
            for (Ball b : balls) {
                if (!b.cue) count++;
            }
            return count;
        }

        /**
         * Reinicia completamente la partida colocando todas las bolas y la blanca.
         */
        private void resetGame() {
            for (Ball b : balls) {
                world.destroyBody(b.body);
            }
            balls.clear();
            impactEffects.clear();

            charging = false;
            power = 0f;
            aimDir = new Vec2(1, 0);
            restartTimer = -1.0;

            createBalls();
            SoundEffects.playBallCollision(0.8f);
        }

        private void addImpactEffect(double x, double y, float intensity) {
            impactEffects.add(new ImpactEffect(x, y, intensity));
        }

        private void updateImpactEffects(double elapsed) {
            impactEffects.removeIf(effect -> effect.update(elapsed));
        }

        private void draw() {
            GraphicsContext g = canvas.getGraphicsContext2D();

            // Fondo exterior oscuro
            g.setFill(Color.web("#141414"));
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            drawTable(g);
            drawPockets(g);

            // Línea de tiro y taco solo cuando las bolas estén quietas y no esté reiniciando
            if (!ballsMoving() && restartTimer < 0) {
                Vec2 currentDir = charging ? aimDir : computeAimDirection();
                drawCueStick(g, currentDir);
                drawAimLine(g, currentDir);
            }

            drawBalls(g);
            drawImpactEffects(g);
            drawPowerBar(g);
            drawHUD(g);

            if (restartTimer > 0) {
                drawVictoryBanner(g);
            }
        }

        private void drawTable(GraphicsContext g) {
            double tablePxW = TABLE_W * SCALE;
            double tablePxH = TABLE_H * SCALE;

            // Borde exterior de madera noble con bisel
            g.setFill(Color.web("#42220f"));
            g.fillRoundRect(
                    OFFSET_X - 18,
                    OFFSET_Y - 18,
                    tablePxW + 36,
                    tablePxH + 36,
                    16, 16
            );

            // Borde interior madera pulida
            g.setFill(Color.web("#5c3116"));
            g.fillRoundRect(
                    OFFSET_X - 10,
                    OFFSET_Y - 10,
                    tablePxW + 20,
                    tablePxH + 20,
                    10, 10
            );

            // Paño verde de alta calidad (Simonis green)
            g.setFill(Color.web("#0e6f3d"));
            g.fillRect(OFFSET_X, OFFSET_Y, tablePxW, tablePxH);

            // Sombra interior de las bandas sobre el paño
            g.setStroke(Color.color(0, 0, 0, 0.22));
            g.setLineWidth(5);
            g.strokeRect(OFFSET_X + 2.5, OFFSET_Y + 2.5, tablePxW - 5, tablePxH - 5);

            // Línea de cabecera sutil (baulk line)
            double headX = OFFSET_X + 0.55 * SCALE;
            g.setStroke(Color.color(1, 1, 1, 0.20));
            g.setLineWidth(1.5);
            g.strokeLine(headX, OFFSET_Y, headX, OFFSET_Y + tablePxH);

            // Punto de salida de la bola blanca
            g.setFill(Color.color(1, 1, 1, 0.35));
            double cueSpotY = OFFSET_Y + (TABLE_H / 2.0) * SCALE;
            g.fillOval(headX - 2.5, cueSpotY - 2.5, 5, 5);

            // Punto de pie (foot spot) donde se monta el triángulo
            double footX = OFFSET_X + 1.65 * SCALE;
            g.fillOval(footX - 2.5, cueSpotY - 2.5, 5, 5);

            // Diamantes incrustados en las bandas (miras reglamentarias)
            drawRailDiamonds(g, tablePxW, tablePxH);
        }

        private void drawRailDiamonds(GraphicsContext g, double tw, double th) {
            g.setFill(Color.web("#f5e6b3"));

            // Diamantes horizontales (arriba y abajo)
            for (int i = 1; i < 8; i++) {
                if (i == 4) continue; // Hueco de la tronera central
                double dx = OFFSET_X + (tw / 8.0) * i;
                drawSingleDiamond(g, dx, OFFSET_Y - 11);
                drawSingleDiamond(g, dx, OFFSET_Y + th + 11);
            }

            // Diamantes verticales (izquierda y derecha)
            for (int i = 1; i < 4; i++) {
                double dy = OFFSET_Y + (th / 4.0) * i;
                drawSingleDiamond(g, OFFSET_X - 11, dy);
                drawSingleDiamond(g, OFFSET_X + tw + 11, dy);
            }
        }

        private void drawSingleDiamond(GraphicsContext g, double cx, double cy) {
            double sz = 2.5;
            g.fillPolygon(
                    new double[]{cx, cx + sz, cx, cx - sz},
                    new double[]{cy - sz, cy, cy + sz, cy},
                    4
            );
        }

        private void drawPockets(GraphicsContext g) {
            double r = POCKET_RADIUS * SCALE * 0.95;

            for (Vec2 pocket : pockets) {
                double x = OFFSET_X + pocket.x * SCALE;
                double y = OFFSET_Y + pocket.y * SCALE;

                // Aro metálico / ribete de cuero de la tronera
                g.setFill(Color.web("#2b1e15"));
                g.fillOval(x - r - 3, y - r - 3, (r + 3) * 2, (r + 3) * 2);

                // Agujero profundo de la tronera
                g.setFill(Color.web("#070707"));
                g.fillOval(x - r, y - r, r * 2, r * 2);
            }
        }

        private void drawCueStick(GraphicsContext g, Vec2 dir) {
            if (bolaBlanca == null) return;

            Vec2 pos = bolaBlanca.getPosition();

            double cx = OFFSET_X + pos.x * SCALE;
            double cy = OFFSET_Y + pos.y * SCALE;

            double angle = Math.atan2(dir.y, dir.x);

            double pullback = power * 0.28 * SCALE;
            double gap = BALL_R * SCALE + 6 + pullback;
            double stickLength = 190;

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double startX = cx - cos * gap;
            double startY = cy - sin * gap;

            double endX = cx - cos * (gap + stickLength);
            double endY = cy - sin * (gap + stickLength);

            // Sombra del taco
            g.setStroke(Color.color(0, 0, 0, 0.35));
            g.setLineWidth(7);
            g.strokeLine(startX + 4, startY + 4, endX + 4, endY + 4);

            // Cuerpo del taco (madera pulida de fresno)
            g.setStroke(Color.web("#b8860b"));
            g.setLineWidth(6.5);
            g.strokeLine(startX, startY, endX, endY);

            // Empuñadura oscura
            double gripStartX = cx - cos * (gap + stickLength * 0.45);
            double gripStartY = cy - sin * (gap + stickLength * 0.45);
            g.setStroke(Color.web("#2c1608"));
            g.setLineWidth(7);
            g.strokeLine(gripStartX, gripStartY, endX, endY);

            // Casquillo y suela del taco
            g.setStroke(Color.web("#e0e0e0"));
            g.setLineWidth(4);
            double tipEndX = cx - cos * (gap + 9);
            double tipEndY = cy - sin * (gap + 9);
            g.strokeLine(startX, startY, tipEndX, tipEndY);

            g.setStroke(Color.web("#3498db")); // Tiza azul
            g.setLineWidth(4);
            double chalkEndX = cx - cos * (gap + 3);
            double chalkEndY = cy - sin * (gap + 3);
            g.strokeLine(startX, startY, chalkEndX, chalkEndY);
        }

        private void drawAimLine(GraphicsContext g, Vec2 dir) {
            if (bolaBlanca == null) return;

            Vec2 origin = bolaBlanca.getPosition();

            float tWall = raycastWalls(origin, dir);

            float tBall = Float.POSITIVE_INFINITY;
            Ball hitBall = null;

            for (Ball ball : balls) {
                if (ball.cue) continue;

                float t = raycastBall(
                        origin,
                        dir,
                        ball.body.getPosition(),
                        (float) (BALL_R * 2.0)
                );

                if (t > 0.001f && t < tBall) {
                    tBall = t;
                    hitBall = ball;
                }
            }

            float tHit = Math.min(tWall, tBall);

            if (Float.isInfinite(tHit) || tHit <= 0f) {
                tHit = 1.0f;
            }

            Vec2 hitPoint = new Vec2(
                    origin.x + dir.x * tHit,
                    origin.y + dir.y * tHit
            );

            double sx = OFFSET_X + origin.x * SCALE;
            double sy = OFFSET_Y + origin.y * SCALE;

            double ex = OFFSET_X + hitPoint.x * SCALE;
            double ey = OFFSET_Y + hitPoint.y * SCALE;

            g.setStroke(Color.color(1, 1, 1, 0.80));
            g.setLineWidth(1.8);
            g.setLineDashes(7, 5);
            g.strokeLine(sx, sy, ex, ey);
            g.setLineDashes();

            if (hitBall != null && tBall < tWall) {
                double ghostRadius = BALL_R * SCALE;

                // Bola fantasma en la posición de impacto
                g.setStroke(Color.color(1, 1, 1, 0.65));
                g.setLineWidth(1.5);
                g.strokeOval(
                        ex - ghostRadius,
                        ey - ghostRadius,
                        ghostRadius * 2,
                        ghostRadius * 2
                );

                Vec2 target = hitBall.body.getPosition();

                float nx = target.x - hitPoint.x;
                float ny = target.y - hitPoint.y;

                float nLen = (float) Math.hypot(nx, ny);

                if (nLen > 0.0001f) {
                    nx /= nLen;
                    ny /= nLen;

                    float targetLineLength = 0.38f;

                    Vec2 targetEnd = new Vec2(
                            target.x + nx * targetLineLength,
                            target.y + ny * targetLineLength
                    );

                    double tx = OFFSET_X + target.x * SCALE;
                    double ty = OFFSET_Y + target.y * SCALE;

                    double tex = OFFSET_X + targetEnd.x * SCALE;
                    double tey = OFFSET_Y + targetEnd.y * SCALE;

                    // Línea de trayectoria predicha de la bola objetivo
                    g.setStroke(Color.web("#ffe600"));
                    g.setLineWidth(2);
                    g.setLineDashes(4, 4);
                    g.strokeLine(tx, ty, tex, tey);
                    g.setLineDashes();
                }
            }
        }

        private float raycastWalls(Vec2 origin, Vec2 dir) {
            float minX = (float) BALL_R;
            float maxX = (float) TABLE_W - minX;
            float minY = (float) BALL_R;
            float maxY = (float) TABLE_H - minY;

            float tx = Float.POSITIVE_INFINITY;
            float ty = Float.POSITIVE_INFINITY;

            if (dir.x > 1e-6f) {
                tx = (maxX - origin.x) / dir.x;
            } else if (dir.x < -1e-6f) {
                tx = (minX - origin.x) / dir.x;
            }

            if (dir.y > 1e-6f) {
                ty = (maxY - origin.y) / dir.y;
            } else if (dir.y < -1e-6f) {
                ty = (minY - origin.y) / dir.y;
            }

            float t = Math.min(tx, ty);

            if (Float.isInfinite(t) || t < 0f) {
                return 10f;
            }

            return t;
        }

        private float raycastBall(Vec2 origin, Vec2 dir, Vec2 center, float radius) {
            float ox = origin.x - center.x;
            float oy = origin.y - center.y;

            float b = 2f * (ox * dir.x + oy * dir.y);
            float c = ox * ox + oy * oy - radius * radius;

            float disc = b * b - 4f * c;

            if (disc < 0f) {
                return -1f;
            }

            float sqrt = (float) Math.sqrt(disc);
            float t = (-b - sqrt) / 2f;

            if (t < 0.001f) {
                t = (-b + sqrt) / 2f;
            }

            return t > 0.001f ? t : -1f;
        }

        /**
         * Dibuja las bolas con sombreado esférico 3D (gradiente radial especular)
         * y sombras arrojadas realistas sobre el paño.
         */
        private void drawBalls(GraphicsContext g) {
            double radiusPixels = BALL_R * SCALE;

            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);

            for (Ball ball : balls) {
                Vec2 p = ball.body.getPosition();

                double x = OFFSET_X + p.x * SCALE;
                double y = OFFSET_Y + p.y * SCALE;

                // Sombra suave proyectada sobre el paño
                g.setFill(Color.color(0, 0, 0, 0.28));
                g.fillOval(x - radiusPixels + 3.0, y - radiusPixels + 3.5, radiusPixels * 2, radiusPixels * 2);

                // Gradiente radial 3D con reflejo especular en la esquina superior izquierda
                RadialGradient sphereGradient;
                if (ball.cue) {
                    sphereGradient = new RadialGradient(
                            0, 0,
                            x - radiusPixels * 0.35, y - radiusPixels * 0.35,
                            radiusPixels * 1.35,
                            false,
                            CycleMethod.NO_CYCLE,
                            new Stop(0.0, Color.WHITE),
                            new Stop(0.35, Color.web("#f4f4f4")),
                            new Stop(0.85, Color.web("#c8c8c8")),
                            new Stop(1.0, Color.web("#949494"))
                    );
                } else {
                    sphereGradient = new RadialGradient(
                            0, 0,
                            x - radiusPixels * 0.35, y - radiusPixels * 0.35,
                            radiusPixels * 1.35,
                            false,
                            CycleMethod.NO_CYCLE,
                            new Stop(0.0, ball.color.interpolate(Color.WHITE, 0.55)),
                            new Stop(0.28, ball.color),
                            new Stop(0.85, ball.color.darker()),
                            new Stop(1.0, ball.color.darker().darker())
                    );
                }

                g.setFill(sphereGradient);
                g.fillOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);

                // Contorno fino de la bola
                g.setStroke(Color.color(0, 0, 0, 0.45));
                g.setLineWidth(0.8);
                g.strokeOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);

                if (!ball.cue) {
                    double numberCircleRadius = radiusPixels * 0.46;

                    // Si es bola rayada (9 a 15), dibujamos un anillo distintivo
                    if (ball.number >= 9) {
                        g.setStroke(ball.color);
                        g.setLineWidth(2.5);
                        g.strokeOval(
                                x - numberCircleRadius - 1.8,
                                y - numberCircleRadius - 1.8,
                                (numberCircleRadius + 1.8) * 2,
                                (numberCircleRadius + 1.8) * 2
                        );
                    }

                    // Círculo blanco central para el número
                    g.setFill(Color.WHITE);
                    g.fillOval(
                            x - numberCircleRadius,
                            y - numberCircleRadius,
                            numberCircleRadius * 2,
                            numberCircleRadius * 2
                    );

                    g.setFill(Color.BLACK);
                    g.fillText(String.valueOf(ball.number), x, y);
                }
            }
        }

        private void drawImpactEffects(GraphicsContext g) {
            for (ImpactEffect effect : impactEffects) {
                effect.draw(g);
            }
        }

        private void drawPowerBar(GraphicsContext g) {
            double barX = canvas.getWidth() - 55;
            double barY = OFFSET_Y;
            double barW = 24;
            double barH = TABLE_H * SCALE;

            // Fondo de la barra
            g.setFill(Color.web("#1e1e1e"));
            g.fillRect(barX, barY, barW, barH);

            double fillH = power * barH;

            // Gradiente de potencia: de verde a rojo
            Color powerColor = Color.color(
                    Math.min(1.0, power * 1.5),
                    Math.max(0.0, 1.0 - power * 0.8),
                    0.0
            );

            g.setFill(powerColor);
            g.fillRect(barX, barY + barH - fillH, barW, fillH);

            g.setStroke(Color.web("#888888"));
            g.setLineWidth(1.5);
            g.strokeRect(barX, barY, barW, barH);

            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.BOTTOM);
            g.setFill(Color.WHITE);
            g.fillText("POTENCIA", barX + barW / 2, barY - 6);
        }

        private void drawHUD(GraphicsContext g) {
            int left = countObjectBalls();

            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.TOP);
            g.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
            g.setFill(Color.WHITE);
            g.fillText(
                    "Apunta con el ratón. Pulsa y arrastra abajo para potencia. Suelta para tirar.  |  [R] Reiniciar partida",
                    OFFSET_X,
                    12
            );

            g.setTextAlign(TextAlignment.RIGHT);
            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            g.setFill(left > 0 ? Color.web("#f1c40f") : Color.web("#2ecc71"));
            g.fillText("Bolas restantes: " + left + " / 15", OFFSET_X + TABLE_W * SCALE, 12);
        }

        /**
         * Banner de felicitación y cuenta atrás cuando se meten todas las bolas.
         */
        private void drawVictoryBanner(GraphicsContext g) {
            double bannerW = 480;
            double bannerH = 150;
            double bx = OFFSET_X + (TABLE_W * SCALE - bannerW) / 2.0;
            double by = OFFSET_Y + (TABLE_H * SCALE - bannerH) / 2.0;

            // Fondo translúcido elegante
            g.setFill(Color.color(0.05, 0.05, 0.05, 0.88));
            g.fillRoundRect(bx, by, bannerW, bannerH, 18, 18);

            // Borde dorado brillante
            g.setStroke(Color.web("#f1c40f"));
            g.setLineWidth(3);
            g.strokeRoundRect(bx, by, bannerW, bannerH, 18, 18);

            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);

            g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
            g.setFill(Color.web("#f1c40f"));
            g.fillText("¡TODAS LAS BOLAS EMBOCADAS!", bx + bannerW / 2, by + 40);

            g.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 15));
            g.setFill(Color.WHITE);
            g.fillText(
                    String.format("¡Enhorabuena! Reiniciando partida en %.1f s...", Math.max(0.1, restartTimer)),
                    bx + bannerW / 2,
                    by + 82
            );

            g.setFont(Font.font("Segoe UI", FontPosture.ITALIC, 12));
            g.setFill(Color.web("#aaaaaa"));
            g.fillText("(O pulsa 'R' para jugar de inmediato)", bx + bannerW / 2, by + 115);
        }

        private static class Ball {
            final Body body;
            final Color color;
            final boolean cue;
            final int number;

            Ball(Body body, Color color, boolean cue, int number) {
                this.body = body;
                this.color = color;
                this.cue = cue;
                this.number = number;
            }
        }

        /**
         * Efecto visual de onda/chispa en el punto exacto de choque entre bolas.
         */
        private static class ImpactEffect {
            final double x, y;
            double radius = 2.0;
            final double maxRadius;
            double alpha = 0.85;

            ImpactEffect(double x, double y, float intensity) {
                this.x = x;
                this.y = y;
                this.maxRadius = Math.min(18.0, 5.0 + intensity * 8.0);
            }

            boolean update(double dt) {
                radius += 35.0 * dt;
                alpha -= 2.8 * dt;
                return alpha <= 0 || radius >= maxRadius;
            }

            void draw(GraphicsContext g) {
                if (alpha <= 0) return;
                g.setStroke(Color.color(1, 1, 0.7, Math.max(0, alpha)));
                g.setLineWidth(1.8);
                g.strokeOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }

        /**
         * Sistema de audio sintetizado procedural para sonidos de billar
         * (clack de bolas de resina y caída en tronera).
         */
        private static class SoundEffects {
            private static Clip[] ballClips;
            private static int clipIndex = 0;
            private static Clip pocketClip;
            private static boolean soundAvailable = true;

            static {
                try {
                    ballClips = new Clip[8];
                    for (int i = 0; i < ballClips.length; i++) {
                        ballClips[i] = generateBallClackClip();
                    }
                    pocketClip = generatePocketClip();
                } catch (Throwable t) {
                    soundAvailable = false;
                }
            }

            private static Clip generateBallClackClip() throws Exception {
                int sampleRate = 44100;
                int numSamples = (int) (sampleRate * 0.035); // 35 ms
                byte[] buffer = new byte[numSamples * 2];

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double decay = Math.exp(-t * 140.0);
                    double freq1 = 2500.0 * Math.exp(-t * 25.0);
                    double freq2 = 1520.0;
                    double sample = (0.65 * Math.sin(2 * Math.PI * freq1 * t)
                            + 0.35 * Math.sin(2 * Math.PI * freq2 * t)) * decay;

                    short s = (short) (sample * 28000);
                    buffer[i * 2] = (byte) (s & 0xff);
                    buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                }

                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(format, buffer, 0, buffer.length);
                return clip;
            }

            private static Clip generatePocketClip() throws Exception {
                int sampleRate = 44100;
                int numSamples = (int) (sampleRate * 0.08); // 80 ms
                byte[] buffer = new byte[numSamples * 2];

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double decay = Math.exp(-t * 45.0);
                    double sample = Math.sin(2 * Math.PI * 180.0 * (1.0 - t * 4.5) * t) * decay;

                    short s = (short) (sample * 24000);
                    buffer[i * 2] = (byte) (s & 0xff);
                    buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
                }

                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(format, buffer, 0, buffer.length);
                return clip;
            }

            public static void playBallCollision(float intensity) {
                if (!soundAvailable || ballClips == null) return;
                try {
                    Clip clip = ballClips[clipIndex];
                    clipIndex = (clipIndex + 1) % ballClips.length;
                    if (clip.isRunning()) clip.stop();
                    clip.setFramePosition(0);

                    if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                        float vol = Math.min(1.0f, Math.max(0.08f, intensity));
                        float dB = (float) (Math.log10(vol) * 20.0);
                        if (dB < -30.0f) dB = -30.0f;
                        gain.setValue(dB);
                    }
                    clip.start();
                } catch (Throwable ignored) {
                }
            }

            public static void playPocket() {
                if (!soundAvailable || pocketClip == null) return;
                try {
                    if (pocketClip.isRunning()) pocketClip.stop();
                    pocketClip.setFramePosition(0);
                    pocketClip.start();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
