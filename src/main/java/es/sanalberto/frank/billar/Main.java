package es.sanalberto.frank.billar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
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

        // Potencia máxima del golpe
        private static final float MAX_IMPULSE = 6.0f;

        // Radio de detección de troneras
        private static final float POCKET_RADIUS = (float) (BALL_R * 1.9);

        private World world;
        private Body bolaBlanca;
        private final List<Ball> balls = new ArrayList<>();

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
                        world.step((float) PHYSICS_STEP, 8, 3);
                        handlePockets();
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
            if (ballsMoving()) return;

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

            if (ballsMoving()) {
                power = 0f;
                return;
            }

            if (power > 0.03f) {
                Vec2 impulse = new Vec2(
                        aimDir.x * power * MAX_IMPULSE,
                        aimDir.y * power * MAX_IMPULSE
                );

                bolaBlanca.setAwake(true);
                bolaBlanca.applyLinearImpulse(impulse, bolaBlanca.getWorldCenter());
            }

            power = 0f;
        }

        private void updateMouse(MouseEvent e) {
            mouseX = (float) ((e.getX() - OFFSET_X) / SCALE);
            mouseY = (float) ((e.getY() - OFFSET_Y) / SCALE);
        }

        private Vec2 computeAimDirection() {
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

                if (v.lengthSquared() > 0.0004f) {
                    return true;
                }
            }

            return false;
        }

        private void initPhysics() {
            world = new World(new Vec2(0, 0));

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
            bolaBlanca = createBall(0.55f, (float) TABLE_H / 2f, Color.WHITE, true, 0);

            Color[] colors = {
                    Color.YELLOW, Color.BLUE, Color.RED, Color.PURPLE, Color.ORANGE,
                    Color.GREEN, Color.MAROON, Color.BLACK, Color.GRAY, Color.TEAL,
                    Color.GOLD, Color.PINK, Color.LIME, Color.SKYBLUE, Color.SALMON
            };

            float startX = 1.65f;
            float spacing = (float) (BALL_R * 2.15);

            int number = 1;

            for (int row = 0; row < 5; row++) {
                for (int i = 0; i <= row; i++) {
                    float x = (float) (startX + row * spacing * Math.sqrt(3.0) / 2.0);
                    float y = (float) (TABLE_H / 2.0 + (i - row / 2.0) * spacing);

                    createBall(x, y, colors[(number - 1) % colors.length], false, number);

                    number++;
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
            fixture.friction = 0.03f;
            fixture.restitution = 0.94f;

            body.createFixture(fixture);

            body.setLinearDamping(0.55f);
            body.setAngularDamping(0.8f);

            balls.add(new Ball(body, color, cue, number));

            return body;
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

                    if (dx * dx + dy * dy <= POCKET_RADIUS * POCKET_RADIUS) {
                        pocketed = true;
                        break;
                    }
                }

                if (!pocketed) continue;

                if (ball.cue) {
                    ball.body.setTransform(new Vec2(0.55f, (float) TABLE_H / 2f), 0f);
                    ball.body.setLinearVelocity(new Vec2(0f, 0f));
                    ball.body.setAngularVelocity(0f);
                    ball.body.setAwake(true);
                } else {
                    world.destroyBody(ball.body);
                    iterator.remove();
                }
            }
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
            g.setLineWidth(12);
            g.strokeRect(
                    OFFSET_X - 6,
                    OFFSET_Y - 6,
                    TABLE_W * SCALE + 12,
                    TABLE_H * SCALE + 12
            );

            drawPockets(g);

            if (!ballsMoving()) {
                Vec2 currentDir = charging ? aimDir : computeAimDirection();

                drawCueStick(g, currentDir);
                drawAimLine(g, currentDir);
            }

            drawBalls(g);
            drawPowerBar(g);

            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.TOP);
            g.setFont(Font.font(12));
            g.setFill(Color.WHITE);
            g.fillText(
                    "Apunta con el ratón. Pulsa y arrastra hacia abajo para cargar potencia. Suelta para golpear.",
                    OFFSET_X,
                    12
            );
        }

        private void drawPockets(GraphicsContext g) {
            g.setFill(Color.BLACK);

            double r = POCKET_RADIUS * SCALE * 0.95;

            for (Vec2 pocket : pockets) {
                double x = OFFSET_X + pocket.x * SCALE;
                double y = OFFSET_Y + pocket.y * SCALE;

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

            g.setStroke(Color.SADDLEBROWN);
            g.setLineWidth(7);
            g.strokeLine(startX, startY, endX, endY);

            g.setStroke(Color.LIGHTGRAY);
            g.setLineWidth(4);

            double tipEndX = cx - cos * (gap + 10);
            double tipEndY = cy - sin * (gap + 10);

            g.strokeLine(startX, startY, tipEndX, tipEndY);
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

            g.setStroke(Color.color(1, 1, 1, 0.85));
            g.setLineWidth(2);
            g.setLineDashes(8, 6);
            g.strokeLine(sx, sy, ex, ey);
            g.setLineDashes();

            if (hitBall != null && tBall < tWall) {
                double ghostRadius = BALL_R * SCALE;

                g.setStroke(Color.color(1, 1, 1, 0.55));
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

                    float targetLineLength = 0.35f;

                    Vec2 targetEnd = new Vec2(
                            target.x + nx * targetLineLength,
                            target.y + ny * targetLineLength
                    );

                    double tx = OFFSET_X + target.x * SCALE;
                    double ty = OFFSET_Y + target.y * SCALE;

                    double tex = OFFSET_X + targetEnd.x * SCALE;
                    double tey = OFFSET_Y + targetEnd.y * SCALE;

                    g.setStroke(Color.YELLOW);
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

        private void drawBalls(GraphicsContext g) {
            double radiusPixels = BALL_R * SCALE;

            g.setFont(Font.font(9));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);

            for (Ball ball : balls) {
                Vec2 p = ball.body.getPosition();

                double x = OFFSET_X + p.x * SCALE;
                double y = OFFSET_Y + p.y * SCALE;

                g.setFill(ball.color);
                g.fillOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);

                g.setStroke(Color.BLACK);
                g.setLineWidth(1);
                g.strokeOval(x - radiusPixels, y - radiusPixels, radiusPixels * 2, radiusPixels * 2);

                if (!ball.cue) {
                    double numberCircleRadius = radiusPixels * 0.45;

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

        private void drawPowerBar(GraphicsContext g) {
            double barX = canvas.getWidth() - 55;
            double barY = OFFSET_Y;
            double barW = 24;
            double barH = TABLE_H * SCALE;

            g.setFill(Color.web("#222222"));
            g.fillRect(barX, barY, barW, barH);

            double fillH = power * barH;

            g.setFill(Color.ORANGE);
            g.fillRect(barX, barY + barH - fillH, barW, fillH);

            g.setStroke(Color.WHITE);
            g.setLineWidth(1);
            g.strokeRect(barX, barY, barW, barH);

            g.setFont(Font.font(10));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.BOTTOM);
            g.setFill(Color.WHITE);
            g.fillText("P", barX + barW / 2, barY - 2);
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
    }
}
