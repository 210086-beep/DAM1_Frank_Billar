package es.sanalberto.frank.billar;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

public class Main {

    public static void main(String[] args) {
        Application.launch(BillarApp.class, args);
    }

    public static class BillarApp extends Application {

        // Escala: 1 metro = 300 píxeles
        private static final double SCALE = 300.0;

        // Margen visual
        private static final double OFFSET_X = 40.0;
        private static final double OFFSET_Y = 40.0;

        // Mesa en metros
        private static final double TABLE_W = 2.4;
        private static final double TABLE_H = 1.2;

        // Radio de bola en metros
        private static final double BALL_R = 0.045;

        // Paso de física fijo
        private static final double PHYSICS_STEP = 1.0 / 120.0;

        private World world;
        private Body bolaBlanca;
        private final List<Ball> balls = new ArrayList<>();

        private Canvas canvas;

        private boolean aiming = false;
        private float mouseX = 0f;
        private float mouseY = 0f;

        private long lastFrameTime = -1;
        private double accumulator = 0.0;

        @Override
        public void start(Stage stage) {
            canvas = new Canvas(
                    TABLE_W * SCALE + OFFSET_X * 2,
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

                    // Evitar saltos grandes si el PC se cuelga un momento
                    if (elapsed > 0.05) {
                        elapsed = 0.05;
                    }

                    accumulator += elapsed;

                    while (accumulator >= PHYSICS_STEP) {
                        world.step((float) PHYSICS_STEP, 8, 3);
                        accumulator -= PHYSICS_STEP;
                    }

                    draw();
                }
            };

            loop.start();

            StackPane root = new StackPane(canvas);
            Scene scene = new Scene(root);

            stage.setTitle("Billar - es.sanalberto.frank.Main");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
        }

        private void setupMouse() {
            canvas.setOnMousePressed(this::press);
            canvas.setOnMouseDragged(this::drag);
            canvas.setOnMouseReleased(this::release);
        }

        private void press(MouseEvent e) {
            aiming = true;
            updateMouse(e);
        }

        private void drag(MouseEvent e) {
            if (aiming) {
                updateMouse(e);
            }
        }

        private void release(MouseEvent e) {
            if (!aiming) return;

            aiming = false;
            updateMouse(e);

            if (ballsMoving()) return;

            Vec2 pos = bolaBlanca.getPosition();

            float dx = mouseX - pos.x;
            float dy = mouseY - pos.y;

            float len = (float) Math.hypot(dx, dy);

            if (len < 0.02f) return;

            float power = Math.min(len * 4.0f, 6.0f);

            Vec2 impulse = new Vec2(
                    dx / len * power,
                    dy / len * power
            );

            bolaBlanca.setAwake(true);
            bolaBlanca.applyLinearImpulse(impulse, bolaBlanca.getWorldCenter());
        }

        private void updateMouse(MouseEvent e) {
            mouseX = (float) ((e.getX() - OFFSET_X) / SCALE);
            mouseY = (float) ((e.getY() - OFFSET_Y) / SCALE);
        }

        private boolean ballsMoving() {
            for (Ball ball : balls) {
                Vec2 v = ball.body.getLinearVelocity();

                if (v.lengthSquared() > 0.0004f) {
                    return true;
                }
            }

            return false;
        }

        private void initPhysics() {
            // Billar = vista cenital, sin gravedad
            world = new World(new Vec2(0, 0));

            createWalls();
            createBalls();
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
            fixture.friction = 0.04f;
            fixture.restitution = 0.82f;

            body.createFixture(fixture);
        }

        private void createBalls() {
            // Bola blanca
            bolaBlanca = createBall(0.55f, (float) TABLE_H / 2f, Color.WHITE, true);

            Color[] colors = {
                    Color.YELLOW, Color.BLUE, Color.RED, Color.PURPLE, Color.ORANGE,
                    Color.GREEN, Color.MAROON, Color.BLACK, Color.GRAY, Color.TEAL,
                    Color.GOLD, Color.PINK, Color.LIME, Color.SKYBLUE, Color.SALMON
            };

            float startX = 1.65f;
            float spacing = (float) (BALL_R * 2.15);

            int colorIndex = 0;

            // Triángulo de 15 bolas
            for (int row = 0; row < 5; row++) {
                for (int i = 0; i <= row; i++) {
                    float x = (float) (startX + row * spacing * Math.sqrt(3.0) / 2.0);
                    float y = (float) (TABLE_H / 2.0 + (i - row / 2.0) * spacing);

                    createBall(x, y, colors[colorIndex % colors.length], false);

                    colorIndex++;
                }
            }
        }

        private Body createBall(float x, float y, Color color, boolean cue) {
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
            fixture.friction = 0.03f;
            fixture.restitution = 0.94f;

            body.createFixture(fixture);

            // Fricción simulada del paño
            body.setLinearDamping(0.55f);
            body.setAngularDamping(0.8f);

            balls.add(new Ball(body, color, cue));

            return body;
        }

        private void draw() {
            GraphicsContext g = canvas.getGraphicsContext2D();

            // Fondo
            g.setFill(Color.web("#1a1a1a"));
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            // Mesa
            g.setFill(Color.web("#0d7a44"));
            g.fillRect(OFFSET_X, OFFSET_Y, TABLE_W * SCALE, TABLE_H * SCALE);

            // Borde
            g.setStroke(Color.SADDLEBROWN);
            g.setLineWidth(8);
            g.strokeRect(
                    OFFSET_X - 4,
                    OFFSET_Y - 4,
                    TABLE_W * SCALE + 8,
                    TABLE_H * SCALE + 8
            );

            // Línea de apuntado
            if (aiming && !ballsMoving()) {
                Vec2 cuePos = bolaBlanca.getPosition();

                double startX = OFFSET_X + cuePos.x * SCALE;
                double startY = OFFSET_Y + cuePos.y * SCALE;

                double endX = OFFSET_X + mouseX * SCALE;
                double endY = OFFSET_Y + mouseY * SCALE;

                g.setStroke(Color.WHITE);
                g.setLineWidth(2);
                g.strokeLine(startX, startY, endX, endY);
            }

            // Bolas
            double radiusPixels = BALL_R * SCALE;

            for (Ball ball : balls) {
                Vec2 p = ball.body.getPosition();

                double x = OFFSET_X + p.x * SCALE;
                double y = OFFSET_Y + p.y * SCALE;

                g.setFill(ball.color);
                g.fillOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);

                g.setStroke(Color.BLACK);
                g.setLineWidth(1);
                g.strokeOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);
            }

            // Texto de ayuda
            g.setFill(Color.WHITE);
            g.fillText(
                    "Haz clic y arrastra; suelta para golpear la bola blanca.",
                    OFFSET_X,
                    OFFSET_Y - 12
            );
        }

        private static class Ball {
            final Body body;
            final Color color;
            final boolean cue;

            Ball(Body body, Color color, boolean cue) {
                this.body = body;
                this.color = color;
                this.cue = cue;
            }
        }
    }
}
