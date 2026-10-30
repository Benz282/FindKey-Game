import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;


/*
 * ============================================================
 *  FIND THE KEY  -  เกมปริศนาหากุญแจ 2 ด่าน (Java Swing)
 * ============================================================
 *  วิธีรัน (ต้องอยู่ในโฟลเดอร์ FindKey ที่มีรูปทั้งหมด):
 *      cd FindKey
 *      javac FindKeyGame.java
 *      java FindKeyGame
 *
 *  ทุกจุดที่อาจต้องปรับ ผมใส่ป้ายไว้ว่า  >>> แก้ไขตรงนี้ <<<
 * ============================================================
 */
public class FindKeyGame {
    public static void main(String[] args) {
        // Swing ควรสร้างหน้าต่างใน Event Dispatch Thread เสมอ
        SwingUtilities.invokeLater(GameFrame::new);
    }
}

/* ============================================================
 *  1) Config : ค่าคงที่ทั้งหมดรวมไว้ที่เดียว (พิกัดทุกตัวอิงภาพ 1920x1080)
 * ============================================================ */
final class Config {
    private Config() { }

    static final int W = 1920;
    static final int H = 1080;

    // >>> แก้ไขตรงนี้ <<<  รหัส 4 หลักของด่าน 1
    // ในภาพ Puzzle.png เห็นเลข 3, 8, 9, 1 -> ผมตั้งเป็น "3891"
    // ถ้าลำดับที่คุณต้องการต่างจากนี้ ให้แก้ตรงนี้ที่เดียว
    static final String PASSWORD = "8391"; //แก้แล้ว

    // ---------- ด่าน 1 ----------
    // พื้นที่คลิกของรูปกรอบภาพปราสาท (Photo_close.png)
    static final Rectangle PHOTO_FRAME_HIT = new Rectangle(197, 107, 460, 551);
    // ตำแหน่งกุญแจในช่องลับ (กล่องสีเข้มที่โผล่ใน Photo_open1.png)
    static final Rectangle KEY_IN_SAFE = new Rectangle(455, 290, 180, 185);
    // ตำแหน่งช่องใส่เลข 4 หลัก (แผงสี่เหลี่ยมยาวด้านล่างขวาของ Photo.png)
    static final Rectangle CODE_BOXES = new Rectangle(190, 685, 560, 110);
    // พื้นที่คลิกของประตู (DoorClose.png)
    static final Rectangle DOOR_HIT = new Rectangle(513, 18, 803, 862);
    // >>> แก้ไขตรงนี้ <<<  Door_open*.png วางกรอบประตูเยื้องจาก DoorClose.png
    // ไปทางขวา-ล่างประมาณ 30,31 px จึงต้องชดเชยกลับ ถ้าประตูเปิดไม่ทับประตูเดิมพอดีให้ปรับสองค่านี้
    static final int OPEN_DX = -30;
    static final int OPEN_DY = -31;
    // กรอบเก็บไอเท็มมุมบนซ้าย
    static final Rectangle INVENTORY_SLOT = new Rectangle(30, 30, 150, 150);

    // ---------- ด่าน 2 ----------
    // พื้นที่คลิกของขวดจดหมาย (ไอคอนปริศนา) ในรูปต้นไม้
    static final Rectangle BOTTLE_HIT = new Rectangle(835, 738, 115, 105);
    // พื้นที่กระดานหมากรุกทั้งหมด (คลิกผิดจะมีข้อความเตือน)
    static final Rectangle CHESS_BOARD = new Rectangle(720, 735, 1090, 310);
    // >>> แก้ไขตรงนี้ <<<  ช่องคำตอบ D6 (จุดที่ Got_it.png วางตัวหมากรุกสีดำ)
    static final Rectangle CHESS_ANSWER = new Rectangle(1200, 875, 125, 80);
}

/* ============================================================
 *  2) Assets : โหลดรูป + ตัวช่วยวาดรูป
 * ============================================================ */

/* ============================================================
 *  2) Assets : โหลดรูป + ตัวช่วยวาดรูป
 * ============================================================ */

final class Assets {

    private Assets() { }

    // >>> แก้ไขตรงนี้ <<<
    // ชื่อโฟลเดอร์เพิ่มเติมที่ต้องการให้ค้นหารูป
    private static final String[] SEARCH_DIRS = {
        "",
        "FindKey",
        "FindKey-Game",
        "images",
        "image",
        "assets",
        "src",
        "src/FindKey",
        "src/images",
        "src/assets"
    };

    // =========================================================
    // หาโฟลเดอร์ที่โปรแกรมกำลังทำงาน
    // =========================================================

    private static File getWorkingFolder() {

        return new File(
            System.getProperty("user.dir")
        ).getAbsoluteFile();
    }

    // =========================================================
    // หาโฟลเดอร์ที่เก็บไฟล์ .class หรือ JAR
    // =========================================================

    private static File getProgramFolder() {

        try {

            File location = new File(
                FindKeyGame.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
            ).getAbsoluteFile();

            if (location.isFile()) {

                return location.getParentFile();

            }

            return location;

        } catch (Exception e) {

            System.err.println(
                "Can't find program folder: " + e.getMessage()
            );

            return getWorkingFolder();
        }
    }

    // =========================================================
    // ตรวจสอบชื่อไฟล์แบบไม่สนตัวพิมพ์เล็ก/ใหญ่
    // =========================================================

    private static File findFileIgnoreCase(
        File folder,
        String fileName
    ) {

        if (folder == null || !folder.isDirectory()) {
            return null;
        }

        // ลองค้นหาชื่อตรงก่อน
        File exact = new File(folder, fileName);

        if (exact.isFile()) {
            return exact;
        }

        // ค้นหาชื่อแบบไม่สนตัวพิมพ์เล็ก/ใหญ่
        File[] files = folder.listFiles();

        if (files == null) {
            return null;
        }

        for (File file : files) {

            if (file.isFile()
                    && file.getName().equalsIgnoreCase(fileName)) {

                return file;
            }
        }

        return null;
    }

    // =========================================================
    // ค้นหาไฟล์ภาพ
    // =========================================================

    static BufferedImage load(String fileName) {

        if (fileName == null || fileName.trim().isEmpty()) {

            System.err.println(
                "File name is empty"
            );

            return null;
        }

        File workingFolder = getWorkingFolder();
        File programFolder = getProgramFolder();

        // ป้องกันกรณีเรียกชื่อไฟล์พร้อม path
        File requested = new File(fileName);

        // -----------------------------------------------------
        // กรณีส่ง path เต็มมา
        // -----------------------------------------------------

        if (requested.isAbsolute()) {

            return readImage(requested);
        }

        // -----------------------------------------------------
        // เตรียมโฟลเดอร์ที่ต้องค้นหา
        // -----------------------------------------------------

        LinkedHashSet<File> folders = new LinkedHashSet<>();

        folders.add(workingFolder);
        folders.add(programFolder);

        // ค้นหาโฟลเดอร์แม่ของ Working Directory
        File parent = workingFolder.getParentFile();

        if (parent != null) {
            folders.add(parent);
        }

        // ค้นหาโฟลเดอร์แม่ของตำแหน่งโปรแกรม
        parent = programFolder.getParentFile();

        if (parent != null) {
            folders.add(parent);
        }

        // เพิ่มโฟลเดอร์ที่กำหนดไว้
        for (File base : new ArrayList<>(folders)) {

            for (String dir : SEARCH_DIRS) {

                folders.add(
                    new File(base, dir)
                );
            }
        }

        // -----------------------------------------------------
        // ค้นหารูปตามโฟลเดอร์ที่เตรียมไว้
        // -----------------------------------------------------

        for (File folder : folders) {

            File imageFile = findFileIgnoreCase(
                folder,
                fileName
            );

            if (imageFile != null) {

                BufferedImage image = readImage(imageFile);

                if (image != null) {

                    System.out.println(
                        "Loaded image successfully: "
                        + imageFile.getAbsolutePath()
                    );

                    return image;
                }
            }
        }

        // -----------------------------------------------------
        // ไม่พบรูป
        // -----------------------------------------------------

        System.err.println(
            "===================================="
        );

        System.err.println(
            "Don't find image: " + fileName
        );

        System.err.println(
            "Working Directory: "
            + workingFolder.getAbsolutePath()
        );

        System.err.println(
            "Program Folder: "
            + programFolder.getAbsolutePath()
        );

        System.err.println(
            "===================================="
        );

        return null;
    }

    // =========================================================
    // อ่านไฟล์ภาพจริง
    // =========================================================

    private static BufferedImage readImage(File file) {

        try {

            if (file == null || !file.isFile()) {
                return null;
            }

            BufferedImage image = ImageIO.read(file);

            if (image == null) {

                System.err.println(
                    "The file is not a supported image: "
                    + file.getAbsolutePath()
                );

                return null;
            }

            return image;

        } catch (IOException e) {

            System.err.println(
                "Failed to read image: "
                + file.getAbsolutePath()
            );

            e.printStackTrace();

            return null;
        }
    }

    // =========================================================
    // โหลดกุญแจ
    // ใช้ระบบเดิมของเกม
    // =========================================================

    static BufferedImage loadKey(String fileName) {

        BufferedImage src = load(fileName);

        if (src == null) {
            return null;
        }

        int h = Math.min(
            src.getHeight(),
            1080
        );

        BufferedImage out = new BufferedImage(
            src.getWidth(),
            h,
            BufferedImage.TYPE_INT_ARGB
        );

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;

        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < h; y++) {

            for (int x = 0; x < src.getWidth(); x++) {

                int rgb = src.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int max = Math.max(
                    r,
                    Math.max(g, b)
                );

                int min = Math.min(
                    r,
                    Math.min(g, b)
                );

                boolean background =
                    min > 170 && (max - min) < 45;

                if (!background) {

                    out.setRGB(
                        x,
                        y,
                        0xFF000000 | (rgb & 0xFFFFFF)
                    );

                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);

                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX) {
            return out;
        }

        return out.getSubimage(
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        );
    }

    // =========================================================
    // วาดรูปยืดเต็มพื้นที่
    // =========================================================

    static void draw(
        Graphics2D g,
        BufferedImage img,
        int x,
        int y,
        int w,
        int h
    ) {

        if (img == null) {

            g.setColor(
                new Color(120, 120, 120, 150)
            );

            g.fillRect(x, y, w, h);

            return;
        }

        g.drawImage(
            img,
            x,
            y,
            w,
            h,
            null
        );
    }

    // =========================================================
    // วาดรูปโดยรักษาสัดส่วน
    // =========================================================

    static void drawFit(
        Graphics2D g,
        BufferedImage img,
        Rectangle r
    ) {

        if (img == null) {

            g.setColor(
                new Color(200, 60, 60, 160)
            );

            g.fillRect(
                r.x,
                r.y,
                r.width,
                r.height
            );

            return;
        }

        double s = Math.min(
            r.width / (double) img.getWidth(),
            r.height / (double) img.getHeight()
        );

        int w = (int) (img.getWidth() * s);
        int h = (int) (img.getHeight() * s);

        g.drawImage(
            img,
            r.x + (r.width - w) / 2,
            r.y + (r.height - h) / 2,
            w,
            h,
            null
        );
    }

    // =========================================================
    // แปลงเวลาจากวินาทีเป็น MM:SS
    // =========================================================

    static String formatTime(int seconds) {

        return String.format(
            "%02d:%02d",
            seconds / 60,
            seconds % 60
        );
    }
}

/* ============================================================
 *  3) GameObject : คลาสนามธรรม (ABSTRACT) ของ "สิ่งของในฉาก"
 *     - ใช้ ENCAPSULATION: bounds/visible เป็น private เข้าถึงผ่าน getter/setter
 *     - ลูกแต่ละตัว override draw()/onClick()/onKey() ต่างกัน = POLYMORPHISM
 * ============================================================ */
abstract class GameObject {
    private final Rectangle bounds;     // private = ห่อหุ้มข้อมูล
    private boolean visible = true;

    protected GameObject(Rectangle bounds) {      // CONSTRUCTOR
        this.bounds = new Rectangle(bounds);
    }

    public Rectangle getBounds() { return new Rectangle(bounds); }   // ส่งสำเนาออกไป กันโดนแก้จากข้างนอก
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean contains(Point p) { return visible && bounds.contains(p); }

    public abstract void draw(Graphics2D g);       // ลูกทุกตัวต้องเขียนเอง
    public void onClick(Point p) { }                // ไม่บังคับ
    public void onKey(KeyEvent e) { }               // ไม่บังคับ
}

/** พื้นที่คลิกที่มองไม่เห็น (เช่น ประตู, ช่อง D6) */
class HotSpot extends GameObject {
    private final Runnable action;

    HotSpot(Rectangle area, Runnable action) {
        super(area);
        this.action = action;
    }

    @Override public void draw(Graphics2D g) { /* มองไม่เห็น */ }

    @Override public void onClick(Point p) {
        if (action != null) action.run();
    }
}

/** รูปที่วาดลงในกรอบที่กำหนด (ปุ่ม Start/Exit/Next, กุญแจ) */
class ImageObject extends GameObject {
    private final BufferedImage image;
    private final Runnable action;

    ImageObject(BufferedImage image, Rectangle bounds, Runnable action) {
        super(bounds);
        this.image = image;
        this.action = action;
    }

    @Override public void draw(Graphics2D g) {
        if (isVisible()) Assets.drawFit(g, image, getBounds());
    }

    @Override public void onClick(Point p) {
        if (action != null) action.run();
    }
}

/**
 * รูปที่เป็น "เลเยอร์เต็มจอ 1920x1080" (Photo_close, Photo_open1, Door_open, Got_it, puzzle1 ...)
 * ภาพพวกนี้เป็น PNG โปร่งใสขนาดเท่าฉาก จึงวางที่ (0,0) ได้เลย
 */
class OverlayObject extends GameObject {
    private final BufferedImage image;
    private final Runnable action;
    private int dx, dy;
    private float alpha = 1f;

    OverlayObject(BufferedImage image, Rectangle hitArea, Runnable action) {
        super(hitArea);
        this.image = image;
        this.action = action;
    }

    void setOffset(int dx, int dy) { this.dx = dx; this.dy = dy; }
    float getAlpha() { return alpha; }
    void setAlpha(float a) { alpha = Math.max(0f, Math.min(1f, a)); }

    @Override public void draw(Graphics2D g) {
        if (!isVisible()) return;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(alpha));
        Assets.draw(g, image, dx, dy, Config.W, Config.H);
        g.setComposite(old);
    }

    @Override public void onClick(Point p) {
        if (action != null) action.run();
    }
}

/** INHERITANCE + POLYMORPHISM: เหมือน OverlayObject แต่มีแสงเรืองกะพริบบอกว่ากดได้ */
class GlowOverlay extends OverlayObject {
    GlowOverlay(BufferedImage image, Rectangle hitArea, Runnable action) {
        super(image, hitArea, action);
    }

    @Override public void draw(Graphics2D g) {
        if (isVisible()) {
            double t = (System.currentTimeMillis() % 1500) / 1500.0;
            float a = (float) (0.30 + 0.25 * Math.sin(t * 2 * Math.PI));
            Rectangle r = getBounds();
            g.setColor(new Color(1f, 0.9f, 0.3f, a));
            g.fillOval(r.x - 25, r.y - 25, r.width + 50, r.height + 50);
        }
        super.draw(g);
    }
}

/** ช่องใส่เลข N หลัก : พิมพ์เลขบนคีย์บอร์ด, Backspace ลบ, ครบหลักจะเรียก onComplete */
class DigitBoxes extends GameObject {
    private final StringBuilder digits = new StringBuilder();
    private final int length;
    private final Consumer<String> onComplete;

    DigitBoxes(Rectangle area, int length, Consumer<String> onComplete) {
        super(area);
        this.length = length;
        this.onComplete = onComplete;
    }

    void clear() { digits.setLength(0); }

    @Override public void onKey(KeyEvent e) {
        if (!isVisible()) return;
        char c = e.getKeyChar();
        if (Character.isDigit(c) && digits.length() < length) {
            digits.append(c);
            if (digits.length() == length) {
                onComplete.accept(digits.toString());
            }
        } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && digits.length() > 0) {
            digits.deleteCharAt(digits.length() - 1);
        }
    }

    @Override public void draw(Graphics2D g) {
        if (!isVisible()) return;
        Rectangle r = getBounds();
        int gap = 40;
        int w = (r.width - gap * (length - 1)) / length;
        g.setFont(new Font("Tahoma", Font.BOLD, 70));
        for (int i = 0; i < length; i++) {
            int x = r.x + i * (w + gap);
            boolean active = (i == digits.length());
            g.setColor(new Color(255, 255, 255, 215));
            g.fillRoundRect(x, r.y, w, r.height, 20, 20);
            g.setColor(active ? new Color(230, 120, 0) : Color.DARK_GRAY);
            g.setStroke(new BasicStroke(active ? 6 : 3));
            g.drawRoundRect(x, r.y, w, r.height, 20, 20);
            g.setStroke(new BasicStroke(1));
            if (i < digits.length()) {
                String s = String.valueOf(digits.charAt(i));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Color.BLACK);
                g.drawString(s, x + (w - fm.stringWidth(s)) / 2,
                        r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }
}

/* ============================================================
 *  4) คลาสย่อยที่ถูก "ประกอบ" อยู่ในด่าน (COMPOSITION)
 * ============================================================ */

/** ตัวล็อกรหัส : ห่อหุ้ม (ENCAPSULATION) รหัสลับไว้ข้างใน คนนอกถามได้แค่ "ถูกไหม" */
final class CodeLock {
    private final String secret;
    private boolean unlocked;

    CodeLock(String secret) { this.secret = secret; }

    boolean tryUnlock(String attempt) {
        if (secret.equals(attempt)) unlocked = true;
        return secret.equals(attempt);
    }

    boolean isUnlocked() { return unlocked; }
}

/** กรอบเก็บไอเท็มมุมบนซ้าย : Level1 "มี" Inventory (has-a) */
final class Inventory {
    private final Rectangle slot;
    private final Map<String, BufferedImage> items = new LinkedHashMap<>();

    Inventory(Rectangle slot) { this.slot = new Rectangle(slot); }

    void add(String name, BufferedImage image) { items.put(name, image); }
    boolean has(String name) { return items.containsKey(name); }

    void draw(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 130));
        g.fillRoundRect(slot.x, slot.y, slot.width, slot.height, 25, 25);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(5));
        g.drawRoundRect(slot.x, slot.y, slot.width, slot.height, 25, 25);
        g.setStroke(new BasicStroke(1));
        for (BufferedImage img : items.values()) {
            Assets.drawFit(g, img, new Rectangle(slot.x + 15, slot.y + 15, slot.width - 30, slot.height - 30));
            break;                                    // มีช่องเดียว แสดงชิ้นแรก
        }
    }
}

/** 1 ฉาก (1 หน้า) = รูปพื้นหลัง + รายการ GameObject  (Level "มี" Page หลายหน้า) */
final class Page {
    private final BufferedImage background;
    private final List<GameObject> objects = new ArrayList<>();

    Page(BufferedImage background) { this.background = background; }

    void add(GameObject o) { objects.add(o); }

    void draw(Graphics2D g) {
        Assets.draw(g, background, 0, 0, Config.W, Config.H);
        for (GameObject o : objects) {
            o.draw(g);                // POLYMORPHISM: สั่ง draw() เหมือนกัน แต่แต่ละชนิดวาดต่างกัน
        }
    }

    boolean click(Point p) {
        for (int i = objects.size() - 1; i >= 0; i--) {   // ชิ้นที่วาดทีหลัง (อยู่บนสุด) ได้สิทธิ์ก่อน
            GameObject o = objects.get(i);
            if (o.contains(p)) {
                o.onClick(p);
                return true;
            }
        }
        return false;
    }

    void key(KeyEvent e) {
        for (GameObject o : objects) o.onKey(e);
    }

    public List getObjects() {
        return objects;
    }
}

/* ============================================================
 *  5) THREAD : นาฬิกาจับเวลารวมของเกม ทำงานแยกอีก Thread
 * ============================================================ */
final class GameClock implements Runnable {
    private volatile int seconds;
    private volatile boolean running;
    private Thread thread;

    synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        seconds = 0;
        running = true;
        thread = new Thread(this, "game-clock");
        thread.setDaemon(true);       // ปิดเกมแล้ว thread นี้จะไม่ค้างโปรเซส
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    int getSeconds() { return seconds; }

    @Override public void run() {
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            if (running) seconds++;
        }
    }
}

/* ============================================================
 *  6) Screen (ABSTRACT) : "หน้าจอ" ทุกแบบในเกม
 *     HomeScreen, Level (-> Level1, Level2), EndScreen ต่างสืบทอด (INHERITANCE) จากตัวนี้
 * ============================================================ */
abstract class Screen {
    protected final GameFrame game;

    protected Screen(GameFrame game) { this.game = game; }

    public abstract void draw(Graphics2D g);
    public void onClick(Point p) { }
    public void onKey(KeyEvent e) { }
}

/** หน้าเปิดเกม */
final class HomeScreen extends Screen {
    private final BufferedImage background = Assets.load("Home.jpg");
    private final List<GameObject> buttons = new ArrayList<>();

    HomeScreen(GameFrame game) {
        super(game);
        // >>> แก้ไขตรงนี้ <<<  ตำแหน่ง/ขนาดปุ่ม
        buttons.add(new ImageObject(Assets.load("Start.png"), new Rectangle(780, 470, 360, 360), game::startGame));
        buttons.add(new ImageObject(Assets.load("Exit1.png"), new Rectangle(800, 800, 320, 202), game::exitGame));
    }

    @Override public void draw(Graphics2D g) {
        Assets.draw(g, background, 0, 0, Config.W, Config.H);
        g.setFont(new Font("Serif", Font.BOLD, 120));
        String title = "FIND THE KEY";
        int x = (Config.W - g.getFontMetrics().stringWidth(title)) / 2;
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(title, x + 5, 225);
        g.setColor(Color.WHITE);
        g.drawString(title, x, 220);
        for (GameObject b : buttons) b.draw(g);
    }

    @Override public void onClick(Point p) {
        for (GameObject b : buttons) {
            if (b.contains(p)) {
                b.onClick(p);
                return;
            }
        }
    }
}

/** หน้าจบด่าน / จบเกม : รูปพื้นหลัง + ปุ่มเดียว */
final class EndScreen extends Screen {
    private final BufferedImage background;
    private final ImageObject button;
    private final boolean showTime;

    EndScreen(GameFrame game, String backgroundFile, String buttonFile,
              Rectangle buttonBounds, Runnable action, boolean showTime) {
        super(game);
        this.background = Assets.load(backgroundFile);
        this.button = new ImageObject(Assets.load(buttonFile), buttonBounds, action);
        this.showTime = showTime;
    }

    @Override public void draw(Graphics2D g) {
        Assets.draw(g, background, 0, 0, Config.W, Config.H);
        button.draw(g);
        if (showTime) {
            g.setFont(new Font("Tahoma", Font.BOLD, 48));
            g.setColor(Color.WHITE);
            g.drawString("เวลาที่ใช้ทั้งหมด " + Assets.formatTime(game.getElapsedSeconds()), 90, 980);
        }
    }

    @Override public void onClick(Point p) {
        if (button.contains(p)) button.onClick(p);
    }
}

/* ============================================================
 *  7) Level (ABSTRACT) : โครงของ "ด่าน" ที่มีหลายหน้า เลื่อนซ้าย-ขวาด้วยลูกศร (ไม่วน)
 * ============================================================ */
abstract class Level extends Screen {
    private final List<Page> pages = new ArrayList<>();      // COMPOSITION
    private final int levelNumber;
    private int pageIndex = 0;
    private String message;
    private final javax.swing.Timer messageTimer;             // TIMER: ข้อความหายเองใน 2.2 วิ

    protected Level(GameFrame game, int levelNumber) {
        super(game);
        this.levelNumber = levelNumber;
        this.messageTimer = new javax.swing.Timer(2200, e -> message = null);
        this.messageTimer.setRepeats(false);
    }

    protected void addPage(Page p) { pages.add(p); }
    protected void showMessage(String text) { message = text; messageTimer.restart(); }

    /** ลูกต้องบอกเองว่าตอนนี้ล็อกการเลื่อนหน้าไหม (กำลังแอนิเมชัน / มี popup) */
    protected abstract boolean isInputLocked();

    /** hook: ลูกจะวาดอะไรทับหน้าจอเพิ่มก็ได้ (กรอบไอเท็ม, popup) */
    protected void drawOverlay(Graphics2D g) { }

    @Override public void draw(Graphics2D g) {
        pages.get(pageIndex).draw(g);
        drawOverlay(g);
        drawHud(g);
        drawMessage(g);
    }

    private void drawHud(Graphics2D g) {
        g.setFont(new Font("Tahoma", Font.BOLD, 36));
        String top = "ด่าน " + levelNumber + "   เวลา " + Assets.formatTime(game.getElapsedSeconds());
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(Config.W - fm.stringWidth(top) - 70, 20, fm.stringWidth(top) + 40, 60, 20, 20);
        g.setColor(Color.WHITE);
        g.drawString(top, Config.W - fm.stringWidth(top) - 50, 62);

        String nav = "< > เลื่อนฉาก   หน้า " + (pageIndex + 1) + "/" + pages.size();
        g.setFont(new Font("Tahoma", Font.PLAIN, 30));
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(20, Config.H - 80, g.getFontMetrics().stringWidth(nav) + 40, 56, 20, 20);
        g.setColor(Color.WHITE);
        g.drawString(nav, 40, Config.H - 40);
    }

    private void drawMessage(Graphics2D g) {
        if (message == null) return;
        g.setFont(new Font("Tahoma", Font.BOLD, 42));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(message) + 80;
        int x = (Config.W - w) / 2;
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, 130, w, 80, 30, 30);
        g.setColor(Color.WHITE);
        g.drawString(message, x + 40, 186);
    }

    @Override public void onClick(Point p) {
        pages.get(pageIndex).click(p);
    }

    @Override public void onKey(KeyEvent e) {
        if (isInputLocked()) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_RIGHT:
                if (pageIndex < pages.size() - 1) pageIndex++;     // ไม่วน: ถึงหน้าสุดท้ายแล้วหยุด
                return;
            case KeyEvent.VK_LEFT:
                if (pageIndex > 0) pageIndex--;
                return;
            default:
                pages.get(pageIndex).key(e);
        }
    }
}

/* ============================================================
 *  8) Level1 : 3 หน้า  (INHERITANCE: extends Level -> extends Screen)
 *     หน้า 1 Photo.png  : กรอบภาพปราสาท + ช่องใส่รหัส 4 หลัก -> ถูกแล้วภาพเลื่อนเปิด เห็นกุญแจ
 *     หน้า 2 Puzzle.png : ภาพคำใบ้ มีเลข 3, 8, 9, 1
 *     หน้า 3 DoorClose.png : ประตู ต้องมีกุญแจในกรอบถึงจะเปิดได้
 * ============================================================ */
final class Level1 extends Level {
    private final Inventory inventory = new Inventory(Config.INVENTORY_SLOT);   // COMPOSITION
    private final CodeLock lock = new CodeLock(Config.PASSWORD);                // COMPOSITION
    private final BufferedImage keyImage = Assets.loadKey("Key.jpg");

    private final DigitBoxes codeBoxes;
    private final OverlayObject pictureClosed;
    private final OverlayObject pictureOpen;
    private final ImageObject keyObject;
    private final OverlayObject doorHalf;
    private final OverlayObject doorFull;

    private boolean doorBusy = false;
    private int doorStep = 0;
    private javax.swing.Timer doorTimer;

    Level1(GameFrame game) {
        super(game, 1);

        // ---------- หน้า 1 ----------
        Page photoPage = new Page(Assets.load("Photo.png"));
        pictureClosed = new OverlayObject(Assets.load("Photo_close.png"), Config.PHOTO_FRAME_HIT,
                () -> showMessage("ภาพนี้ขยับไม่ได้... ลองหารหัสแล้วใส่ช่องด้านล่าง"));
        pictureOpen = new OverlayObject(Assets.load("Photo_open1.png"), new Rectangle(), null);
        pictureOpen.setVisible(false);
        keyObject = new ImageObject(keyImage, Config.KEY_IN_SAFE, this::collectKey);
        keyObject.setVisible(false);
        codeBoxes = new DigitBoxes(Config.CODE_BOXES, 4, this::checkCode);

        photoPage.add(pictureClosed);   // ลำดับ add = ลำดับการวาด (ตัวหลังอยู่บน)
        photoPage.add(pictureOpen);
        photoPage.add(keyObject);
        photoPage.add(codeBoxes);
        addPage(photoPage);

        // ---------- หน้า 2 ----------
        addPage(new Page(Assets.load("Puzzle.png")));

        // ---------- หน้า 3 ----------
        Page doorPage = new Page(Assets.load("DoorClose.png"));
        doorHalf = new OverlayObject(Assets.load("Door_open1.png"), new Rectangle(), null);
        doorFull = new OverlayObject(Assets.load("Door_open.png"), new Rectangle(), null);
        doorHalf.setOffset(Config.OPEN_DX, Config.OPEN_DY);
        doorFull.setOffset(Config.OPEN_DX, Config.OPEN_DY);
        doorHalf.setVisible(false);
        doorFull.setVisible(false);
        doorPage.add(new HotSpot(Config.DOOR_HIT, this::tryOpenDoor));
        doorPage.add(doorHalf);
        doorPage.add(doorFull);
        addPage(doorPage);
    }

    @Override protected boolean isInputLocked() { return doorBusy; }

    @Override protected void drawOverlay(Graphics2D g) { inventory.draw(g); }

    // ถูกเรียกเมื่อพิมพ์ครบ 4 หลัก
    private void checkCode(String code) {
        if (lock.tryUnlock(code)) {
            codeBoxes.setVisible(false);
            pictureClosed.setVisible(false);
            pictureOpen.setAlpha(0f);
            pictureOpen.setVisible(true);
            showMessage("รหัสถูกต้อง! ภาพเลื่อนเปิดออก");
            fadeInSafe();
        } else {
            codeBoxes.clear();
            showMessage("รหัสไม่ถูกต้อง ลองใหม่อีกครั้ง");
        }
    }

    // TIMER: ค่อย ๆ ทำให้ภาพที่เปิดแล้วชัดขึ้นทุก 40ms
    private void fadeInSafe() {
        javax.swing.Timer t = new javax.swing.Timer(40, null);
        t.addActionListener(e -> {
            float a = pictureOpen.getAlpha() + 0.1f;
            if (a >= 1f) {
                a = 1f;
                t.stop();
                keyObject.setVisible(true);       // กุญแจโผล่หลังภาพเปิดเสร็จ
            }
            pictureOpen.setAlpha(a);
        });
        t.start();
    }

    private void collectKey() {
        inventory.add("key", keyImage);
        keyObject.setVisible(false);
        showMessage("ได้รับกุญแจแล้ว! ไปที่ประตูในหน้า 3");
    }

    private void tryOpenDoor() {
        if (doorBusy) return;
        if (!inventory.has("key")) {
            showMessage("ประตูล็อกอยู่ ต้องหากุญแจก่อน");
            return;
        }
        doorBusy = true;
        doorStep = 0;
        showMessage("ไขประตูด้วยกุญแจ...");
        // TIMER: แอนิเมชันประตู 3 จังหวะ แล้วไปหน้าจบด่าน
        doorTimer = new javax.swing.Timer(700, e -> {
            doorStep++;
            if (doorStep == 1) {
                doorHalf.setVisible(true);
            } else if (doorStep == 2) {
                doorHalf.setVisible(false);
                doorFull.setVisible(true);
            } else if (doorStep >= 4) {
                doorTimer.stop();
                // >>> แก้ไขตรงนี้ <<<  ตำแหน่งปุ่ม Next บนหน้าจบด่าน 1
                game.showScreen(new EndScreen(game, "EndLavel1.jpg", "Next.png",
                        new Rectangle(1560, 820, 180, 180),
                        () -> game.showScreen(new Level2(game)), false));
            }
        });
        doorTimer.start();
    }
}

/* ============================================================
 *  9) Level2 : 2 หน้า
 *     หน้า 1 Level2_2.png : ต้นไม้ + ขวดจดหมาย (ไอคอนปริศนา) -> คลิกแล้วเด้ง popup คำใบ้ (puzzel2.png)
 *     หน้า 2 Level2_1.png : กระดานหมากรุก -> คลิกช่องที่ถูก (D6) ถึงจะจบเกม
 *
 *     เฉลยคำใบ้: 7=D 15=I 21=S 5=C 13=O 13=V 20=E 16=R -> "DISCOVER"
 *     วงกลมสีแดงอยู่ที่ตัวอักษรตัวที่ 1 (D) และตำแหน่งที่ 6  =>  แถว D คอลัมน์ 6 = D6
 * ============================================================ */
final class Level2 extends Level {
    private final BufferedImage clueImage = Assets.load("puzzel2.png");
    private final OverlayObject kingFound;
    private boolean popupOpen = false;
    private boolean finished = false;

    Level2(GameFrame game) {
        super(game, 2);

        Page treePage = new Page(Assets.load("Level2_2.png"));
        treePage.add(new GlowOverlay(Assets.load("puzzle1.png"), Config.BOTTLE_HIT, this::openClue));
        addPage(treePage);

        Page chessPage = new Page(Assets.load("Level2_1.png"));
        chessPage.add(new HotSpot(Config.CHESS_BOARD,
                () -> showMessage("ยังไม่ใช่ตำแหน่งที่ถูก ลองอ่านคำใบ้อีกครั้ง")));
        chessPage.add(new HotSpot(Config.CHESS_ANSWER, this::foundAnswer));   // add ทีหลัง = ถูกเช็กก่อน
        kingFound = new OverlayObject(Assets.load("Got_it.png"), new Rectangle(), null);
        kingFound.setVisible(false);
        chessPage.add(kingFound);
        addPage(chessPage);
    }

    @Override protected boolean isInputLocked() { return popupOpen || finished; }

    private void openClue() { popupOpen = true; }

    private void foundAnswer() {
        if (finished) return;
        finished = true;
        kingFound.setVisible(true);
        showMessage("ถูกต้อง! พบตัวหมากรุกที่ซ่อนอยู่");
        javax.swing.Timer t = new javax.swing.Timer(1800, e -> {
            game.stopClock();
            game.showScreen(new EndScreen(game, "EndGame.jpg", "Exit1.png",
                    new Rectangle(1520, 840, 330, 208), game::exitGame, true));
        });
        t.setRepeats(false);
        t.start();
    }

    @Override protected void drawOverlay(Graphics2D g) {
        if (popupOpen) {
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, Config.W, Config.H);
            Assets.draw(g, clueImage, 0, 0, Config.W, Config.H);
            g.setFont(new Font("Tahoma", Font.PLAIN, 32));
            g.setColor(Color.WHITE);
            g.drawString("คลิกที่ไหนก็ได้เพื่อปิดคำใบ้", 700, 1040);
        }
    }

    @Override public void onClick(Point p) {
        if (popupOpen) {              // popup เปิดอยู่: คลิกที่ไหนก็ปิด และไม่ส่งต่อไปที่ฉากด้านหลัง
            popupOpen = false;
            return;
        }
        super.onClick(p);
    }

    @Override public void onKey(KeyEvent e) {
        if (popupOpen) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER) popupOpen = false;
            return;
        }
        super.onKey(e);
    }
}

/* ============================================================
 *  10) GamePanel : ที่วาดเกม ย่อ/ขยายภาพ 1920x1080 ให้พอดีหน้าต่าง + ส่ง mouse/key ไปให้ Screen
 *      มี Timer (game loop) รีเฟรชภาพ ~30 เฟรม/วินาที
 * ============================================================ */
final class GamePanel extends JPanel {
    private Screen current;
    private double scale = 1, offX = 0, offY = 0;
    private final javax.swing.Timer loop = new javax.swing.Timer(33, e -> repaint());

    GamePanel() {
        setFocusable(true);
        setBackground(Color.BLACK);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (current != null) current.onClick(toLogical(e.getPoint()));
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (current != null) current.onKey(e);
            }
        });
        loop.start();
    }

    void setScreen(Screen s) { current = s; }
    void stopLoop() { loop.stop(); }

    // แปลงพิกัดเมาส์บนหน้าต่าง -> พิกัดในโลกเกม 1920x1080
    private Point toLogical(Point p) {
        return new Point((int) ((p.x - offX) / scale), (int) ((p.y - offY) / scale));
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        scale = Math.min(getWidth() / (double) Config.W, getHeight() / (double) Config.H);
        offX = (getWidth() - Config.W * scale) / 2;
        offY = (getHeight() - Config.H * scale) / 2;
        g2.translate(offX, offY);
        g2.scale(scale, scale);
        g2.setClip(0, 0, Config.W, Config.H);
        if (current != null) current.draw(g2);
        g2.dispose();
    }
}

/* ============================================================
 *  11) GameFrame : หน้าต่างหลัก  (มี GamePanel และ GameClock อยู่ข้างใน = COMPOSITION)
 * ============================================================ */
final class GameFrame extends JFrame {
    private final GamePanel panel = new GamePanel();
    private final GameClock clock = new GameClock();

    GameFrame() {
        super("Find the Key");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitGame(); }   // กากบาทก็หยุดรันจริง
        });
        // ขนาดหน้าต่างเริ่มต้น (ย่อ/ขยายเองได้ ภาพจะปรับตาม)
        panel.setPreferredSize(new Dimension(1280, 720));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        panel.setScreen(new HomeScreen(this));
        setVisible(true);
        panel.requestFocusInWindow();
    }

    void showScreen(Screen s) { panel.setScreen(s); }
    void startGame() { clock.start(); showScreen(new Level1(this)); }
    void stopClock() { clock.stop(); }
    int getElapsedSeconds() { return clock.getSeconds(); }

    // กด Exit: หยุด Timer, หยุด Thread, ปิดหน้าต่าง, จบโปรแกรม
    void exitGame() {
        panel.stopLoop();
        clock.stop();
        dispose();
        System.exit(0);
    }
}