import struct, zlib, os, subprocess

def create_character():
    W, H = 64, 64
    # Transparent canvas RGBA (0, 0, 0, 0)
    canvas = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]

    def set_px(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            canvas[y][x] = color

    def fill_rect(x1, y1, x2, y2, color):
        for y in range(y1, y2 + 1):
            for x in range(x1, x2 + 1):
                set_px(x, y, color)

    # Colors
    OUTLINE = (28, 18, 16, 255)
    DARK_OUTLINE = (18, 12, 10, 255)
    
    # Chalk / Tip colors (Cyan / Blue)
    CHALK_LIGHT = (115, 215, 255, 255)
    CHALK_MID   = (40, 160, 235, 255)
    CHALK_DARK  = (20, 100, 175, 255)
    LEATHER_TIP = (85, 50, 40, 255)
    LEATHER_SHADOW = (55, 30, 25, 255)
    
    # Ferrule (Virola) - Ivory / Gold
    FERRULE_HI  = (255, 255, 245, 255)
    FERRULE_MID = (245, 230, 190, 255)
    FERRULE_SHD = (195, 170, 130, 255)

    # Shaft (Maple wood)
    WOOD_HI     = (252, 228, 185, 255)
    WOOD_MID    = (238, 198, 142, 255)
    WOOD_SHD    = (198, 150, 95, 255)
    WOOD_DARK   = (160, 110, 65, 255)
    WOOD_GRAIN  = (215, 172, 115, 255)

    # Splices / Inlays (Rich Mahogany & Emerald)
    INLAY_DARK  = (80, 25, 20, 255)
    INLAY_MID   = (125, 45, 30, 255)
    INLAY_HI    = (165, 70, 45, 255)
    EMERALD_HI  = (40, 210, 140, 255)
    EMERALD_MID = (20, 150, 95, 255)

    # Wrap (Irish linen grip)
    WRAP_BASE   = (38, 42, 48, 255)
    WRAP_SPEC   = (190, 195, 205, 255)
    WRAP_SHD    = (22, 24, 28, 255)

    # Glove White
    GLOVE_HI    = (255, 255, 255, 255)
    GLOVE_MID   = (225, 230, 240, 255)
    GLOVE_SHD   = (165, 175, 195, 255)

    # Ball 8
    BALL_BLACK  = (25, 25, 30, 255)
    BALL_HI     = (90, 95, 105, 255)
    BALL_WHITE  = (245, 245, 250, 255)
    
    # Shoes / Boots
    SHOE_DARK   = (35, 25, 30, 255)
    SHOE_MID    = (75, 45, 55, 255)
    SHOE_HI     = (115, 75, 90, 255)
    SHOE_SOLE   = (210, 180, 140, 255)

    # Eyes & Mouth
    EYE_WHITE   = (255, 255, 255, 255)
    PUPIL       = (20, 20, 30, 255)
    CHEEK_BLUSH = (245, 130, 135, 255)
    MOUTH_DARK  = (65, 25, 25, 255)
    MOUTH_RED   = (225, 60, 75, 255)
    TONGUE      = (255, 140, 155, 255)

    # Cue Center X = 31 (shaft width 8-12 pixels)
    # -------------------------------------------------------------
    # 1. THE TIP / CHALK BERET (Y: 4 to 10)
    # Chalk cube worn tilted like a cute cap/beret!
    # Cap position: X: 24 to 38, Y: 4 to 10
    for y in range(4, 10):
        for x in range(26, 37):
            set_px(x, y, CHALK_MID)
    # Cap 3D bevel / cube shape
    for x in range(27, 36):
        set_px(x, 4, CHALK_LIGHT)
        set_px(x, 5, CHALK_LIGHT)
    for y in range(5, 10):
        set_px(26, y, CHALK_LIGHT)
        set_px(36, y, CHALK_DARK)
    # Chalk center indentation (where cue chalks)
    set_px(31, 4, CHALK_DARK)
    set_px(31, 5, CHALK_DARK)
    set_px(30, 4, CHALK_DARK)
    set_px(32, 4, CHALK_DARK)
    # Outline for chalk cap
    for x in range(25, 38):
        set_px(x, 3, OUTLINE)
        set_px(x, 10, OUTLINE)
    for y in range(4, 10):
        set_px(25, y, OUTLINE)
        set_px(37, y, OUTLINE)

    # 2. LEATHER TIP & FERRULE (VIROLA) (Y: 11 to 15)
    # Leather tip (Y: 11)
    for x in range(27, 36):
        set_px(x, 11, LEATHER_TIP)
    set_px(26, 11, OUTLINE)
    set_px(36, 11, OUTLINE)
    set_px(27, 11, LEATHER_SHADOW)
    set_px(35, 11, LEATHER_SHADOW)
    set_px(31, 11, CHALK_MID) # little blue chalk mark on tip!

    # Ferrule Ivory / Gold (Y: 12 to 15)
    for y in range(12, 16):
        set_px(26, y, OUTLINE)
        set_px(27, y, FERRULE_HI)
        for x in range(28, 34):
            set_px(x, y, FERRULE_MID)
        set_px(34, y, FERRULE_SHD)
        set_px(35, y, FERRULE_SHD)
        set_px(36, y, OUTLINE)
    # Ferrule ring line
    for x in range(27, 36):
        set_px(x, 15, WOOD_DARK)

    # 3. SHAFT & HEAD / FACE REGION (Y: 16 to 34)
    # Shaft width: starts at 9 px (X: 27 to 35), gently widening
    for y in range(16, 35):
        x_left = 27 - (1 if y > 26 else 0)
        x_right = 35 + (1 if y > 26 else 0)
        set_px(x_left, y, OUTLINE)
        set_px(x_right, y, OUTLINE)
        set_px(x_left + 1, y, WOOD_HI)
        for x in range(x_left + 2, x_right - 1):
            set_px(x, y, WOOD_MID)
        set_px(x_right - 1, y, WOOD_SHD)
        set_px(x_right, y, OUTLINE)
        
        # Subtle wood grain vertical streaks
        if y % 3 == 0:
            set_px(x_left + 2, y, WOOD_GRAIN)
        if y % 4 == 1:
            set_px(x_right - 2, y, WOOD_GRAIN)

    # FACE (Y: 18 to 26)
    # Eyebrows (Y: 18)
    set_px(28, 18, WOOD_DARK)
    set_px(29, 17, WOOD_DARK)
    set_px(33, 17, WOOD_DARK)
    set_px(34, 18, WOOD_DARK)

    # Eyes (Big Anime/Retro Cartoon Eyes) (Y: 19 to 22)
    # Left Eye (X: 28 to 30)
    for y in range(19, 23):
        for x in range(28, 31):
            set_px(x, y, EYE_WHITE)
    # Right Eye (X: 32 to 34)
    for y in range(19, 23):
        for x in range(32, 35):
            set_px(x, y, EYE_WHITE)

    # Pupils (Looking slightly right and forward, very lively)
    set_px(29, 20, PUPIL)
    set_px(30, 20, PUPIL)
    set_px(29, 21, PUPIL)
    set_px(30, 21, PUPIL)

    set_px(33, 20, PUPIL)
    set_px(34, 20, PUPIL)
    set_px(33, 21, PUPIL)
    set_px(34, 21, PUPIL)

    # Eye glints (shiny sparkles!)
    set_px(29, 20, EYE_WHITE)
    set_px(33, 20, EYE_WHITE)

    # Eye bottom outlines
    set_px(28, 23, OUTLINE)
    set_px(29, 23, OUTLINE)
    set_px(30, 23, OUTLINE)
    set_px(32, 23, OUTLINE)
    set_px(33, 23, OUTLINE)
    set_px(34, 23, OUTLINE)

    # Rosy cheeks
    set_px(27, 22, CHEEK_BLUSH)
    set_px(35, 22, CHEEK_BLUSH)

    # Cute confident smile (Y: 24 to 25)
    set_px(30, 24, MOUTH_DARK)
    set_px(31, 25, MOUTH_DARK)
    set_px(32, 25, MOUTH_DARK)
    set_px(33, 24, MOUTH_DARK)
    set_px(31, 24, MOUTH_RED)
    set_px(32, 24, TONGUE)

    # Cute cue collar / bowtie (Red bowtie at Y: 27)
    BOWTIE_RED = (220, 35, 45, 255)
    BOWTIE_DARK = (140, 15, 25, 255)
    BOWTIE_KNOT = (255, 75, 85, 255)
    set_px(31, 27, BOWTIE_KNOT)
    set_px(30, 27, BOWTIE_RED)
    set_px(29, 27, BOWTIE_RED)
    set_px(29, 26, BOWTIE_DARK)
    set_px(29, 28, BOWTIE_DARK)
    set_px(32, 27, BOWTIE_RED)
    set_px(33, 27, BOWTIE_RED)
    set_px(33, 26, BOWTIE_DARK)
    set_px(33, 28, BOWTIE_DARK)

    # 4. LOWER BODY: INLAYS & SPLICED POINTS (Y: 35 to 43)
    for y in range(35, 44):
        xl = 26
        xr = 36
        set_px(xl, y, OUTLINE)
        set_px(xr, y, OUTLINE)
        for x in range(xl + 1, xr):
            set_px(x, y, WOOD_MID)
        set_px(xl + 1, y, WOOD_HI)
        set_px(xr - 1, y, WOOD_SHD)

    # Inlay V-points (Triangular cue splices pointing up!)
    # Point 1 (left)
    set_px(28, 35, INLAY_DARK)
    set_px(28, 36, INLAY_MID)
    set_px(29, 36, INLAY_DARK)
    for y in range(37, 44):
        set_px(28, y, INLAY_MID)
        set_px(29, y, INLAY_DARK)
        
    # Point 2 (center emerald diamond)
    set_px(31, 37, EMERALD_HI)
    set_px(30, 38, EMERALD_MID)
    set_px(31, 38, EMERALD_HI)
    set_px(32, 38, EMERALD_MID)
    set_px(31, 39, EMERALD_MID)

    # Point 3 (right)
    set_px(34, 35, INLAY_DARK)
    set_px(33, 36, INLAY_DARK)
    set_px(34, 36, INLAY_MID)
    for y in range(37, 44):
        set_px(33, y, INLAY_DARK)
        set_px(34, y, INLAY_MID)

    # Inlay gold ring border (Y: 43)
    for x in range(27, 36):
        set_px(x, 43, FERRULE_MID)

    # 5. IRISH LINEN GRIP (WRAP SECTION) (Y: 44 to 53)
    for y in range(44, 54):
        xl = 26
        xr = 36
        set_px(xl, y, OUTLINE)
        set_px(xr, y, OUTLINE)
        for x in range(xl + 1, xr):
            # Speckled linen texture
            if (x + y * 2) % 3 == 0:
                set_px(x, y, WRAP_SPEC)
            elif (x + y) % 2 == 0:
                set_px(x, y, WRAP_BASE)
            else:
                set_px(x, y, WRAP_SHD)
        # 3D shadow on right edge of wrap
        set_px(xr - 1, y, WRAP_SHD)
        set_px(xl + 1, y, WRAP_SPEC)

    # Ring separating grip from butt cap
    for x in range(27, 36):
        set_px(x, 53, FERRULE_MID)

    # Butt sleeve (Y: 54 to 56)
    for y in range(54, 57):
        xl = 26
        xr = 36
        set_px(xl, y, OUTLINE)
        set_px(xr, y, OUTLINE)
        for x in range(xl + 1, xr):
            set_px(x, y, INLAY_DARK)
        set_px(xl + 1, y, INLAY_HI)
        set_px(31, y, FERRULE_HI) # center inlay dot

    # Rubber Bumper / Base Ring (Y: 57)
    for x in range(27, 36):
        set_px(x, 57, BALL_BLACK)
    for x in range(26, 37):
        set_px(x, 58, OUTLINE)

    # 6. LEGS & SHOES (Y: 57 to 62)
    # Sturdy little legs wearing sleek retro boots!
    # Left Leg (X: 25 to 29)
    # Left boot
    fill_rect(24, 59, 29, 61, SHOE_MID)
    fill_rect(23, 60, 24, 61, SHOE_MID) # Toe pointing left
    for x in range(23, 30):
        set_px(x, 62, SHOE_SOLE)
        set_px(x, 63, OUTLINE)
    set_px(22, 61, OUTLINE)
    set_px(22, 60, OUTLINE)
    set_px(25, 59, SHOE_HI)
    set_px(26, 59, SHOE_HI)
    set_px(29, 59, SHOE_DARK)
    set_px(29, 60, SHOE_DARK)
    set_px(24, 58, OUTLINE)
    set_px(29, 58, OUTLINE)

    # Right Leg (X: 33 to 38)
    # Right boot
    fill_rect(33, 59, 38, 61, SHOE_MID)
    fill_rect(38, 60, 39, 61, SHOE_MID) # Toe pointing right
    for x in range(33, 40):
        set_px(x, 62, SHOE_SOLE)
        set_px(x, 63, OUTLINE)
    set_px(40, 61, OUTLINE)
    set_px(40, 60, OUTLINE)
    set_px(36, 59, SHOE_HI)
    set_px(37, 59, SHOE_HI)
    set_px(33, 59, SHOE_DARK)
    set_px(33, 60, SHOE_DARK)
    set_px(33, 58, OUTLINE)
    set_px(38, 58, OUTLINE)

    # 7. ARMS & WHITE GLOVED HANDS
    # LEFT ARM: Resting heroically on hip (X: 18 to 26, Y: 29 to 37)
    ARM_COLOR = WOOD_MID
    ARM_HI = WOOD_HI
    ARM_SHD = WOOD_SHD

    # Upper arm
    set_px(25, 29, OUTLINE)
    set_px(24, 30, OUTLINE)
    set_px(23, 31, OUTLINE)
    set_px(22, 32, OUTLINE)
    
    set_px(26, 30, ARM_COLOR)
    set_px(25, 31, ARM_COLOR)
    set_px(24, 32, ARM_COLOR)
    set_px(23, 33, ARM_COLOR)

    set_px(27, 31, OUTLINE)
    set_px(26, 32, OUTLINE)
    set_px(25, 33, OUTLINE)
    set_px(24, 34, OUTLINE)

    # Forearm bending back to hip
    set_px(21, 33, OUTLINE)
    set_px(20, 34, OUTLINE)
    set_px(21, 35, ARM_COLOR)
    set_px(22, 35, ARM_COLOR)
    set_px(23, 35, ARM_COLOR)

    # White Glove on Left Hip (X: 21 to 26, Y: 36 to 39)
    fill_rect(22, 36, 26, 38, GLOVE_MID)
    set_px(23, 36, GLOVE_HI)
    set_px(24, 36, GLOVE_HI)
    set_px(22, 38, GLOVE_SHD)
    set_px(25, 38, GLOVE_SHD)
    # Glove cuff
    set_px(21, 35, GLOVE_HI)
    set_px(21, 36, OUTLINE)
    # Fingers gripped on hip
    set_px(26, 37, OUTLINE)
    set_px(26, 38, OUTLINE)
    set_px(25, 39, OUTLINE)
    set_px(24, 39, OUTLINE)
    set_px(23, 39, OUTLINE)
    set_px(22, 39, OUTLINE)

    # RIGHT ARM: Extended out holding the iconic Bola 8! (X: 36 to 52, Y: 28 to 36)
    # Upper arm
    set_px(37, 29, OUTLINE)
    set_px(38, 29, ARM_COLOR)
    set_px(39, 29, ARM_COLOR)
    set_px(40, 30, ARM_COLOR)
    set_px(41, 30, ARM_COLOR)
    set_px(42, 31, ARM_COLOR)
    
    set_px(37, 28, OUTLINE)
    set_px(38, 28, OUTLINE)
    set_px(39, 28, OUTLINE)
    set_px(40, 29, OUTLINE)
    set_px(41, 29, OUTLINE)
    set_px(42, 30, OUTLINE)
    
    set_px(38, 30, OUTLINE)
    set_px(39, 30, OUTLINE)
    set_px(40, 31, OUTLINE)
    set_px(41, 31, OUTLINE)
    set_px(42, 32, OUTLINE)

    # Glove holding the ball (palm up / holding) (X: 43 to 48, Y: 31 to 35)
    # Glove cuff
    set_px(43, 30, GLOVE_HI)
    set_px(43, 31, GLOVE_HI)
    set_px(43, 32, OUTLINE)
    fill_rect(44, 31, 48, 34, GLOVE_MID)
    set_px(44, 31, GLOVE_HI)
    set_px(45, 31, GLOVE_HI)
    set_px(46, 32, GLOVE_HI)
    set_px(44, 34, GLOVE_SHD)
    set_px(47, 34, GLOVE_SHD)
    # Glove outlines
    for x in range(44, 49):
        set_px(x, 35, OUTLINE)
    set_px(48, 32, OUTLINE)
    set_px(48, 33, OUTLINE)
    set_px(48, 34, OUTLINE)

    # 8. THE ICONIC BOLA 8 BALANCED IN HIS HAND (X: 47 to 57, Y: 22 to 32)
    # Ball 8 is a circle of diameter 11
    ball_center_x, ball_center_y = 52, 27
    radius = 5.2
    for by in range(22, 33):
        for bx in range(47, 58):
            dist_sq = (bx - ball_center_x)**2 + (by - ball_center_y)**2
            if dist_sq <= radius**2:
                # Inside ball
                if dist_sq >= (radius - 0.9)**2:
                    set_px(bx, by, OUTLINE)
                else:
                    # Shading
                    if (bx - (ball_center_x - 2))**2 + (by - (ball_center_y - 2))**2 < 4:
                        set_px(bx, by, BALL_HI)
                    else:
                        set_px(bx, by, BALL_BLACK)

    # White circle on Ball 8
    circle_center_x, circle_center_y = 52, 27
    for by in range(25, 30):
        for bx in range(50, 55):
            if (bx - circle_center_x)**2 + (by - circle_center_y)**2 <= 2.2**2:
                set_px(bx, by, BALL_WHITE)

    # The number "8" inside the white circle
    set_px(52, 26, BALL_BLACK)
    set_px(51, 26, BALL_BLACK)
    set_px(53, 26, BALL_BLACK)
    set_px(51, 27, BALL_BLACK)
    set_px(53, 27, BALL_BLACK)
    set_px(52, 27, BALL_BLACK)
    set_px(51, 28, BALL_BLACK)
    set_px(53, 28, BALL_BLACK)
    set_px(52, 28, BALL_BLACK)
    set_px(52, 26, BALL_WHITE) # upper hole
    set_px(52, 28, BALL_WHITE) # lower hole

    # Ball specular highlight
    set_px(50, 24, (255, 255, 255, 220))

    # Little magical twinkle near the tip of the cue!
    SPARKLE = (255, 255, 200, 255)
    set_px(22, 6, SPARKLE)
    set_px(21, 6, (255, 255, 255, 180))
    set_px(23, 6, (255, 255, 255, 180))
    set_px(22, 5, (255, 255, 255, 180))
    set_px(22, 7, (255, 255, 255, 180))

    return canvas

def make_png(width, height, pixels):
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            raw += bytes(pixels[y][x])
            
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(raw, 9)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', idat) + chunk(b'IEND', b'')

def scale_pixels(pixels, factor):
    h = len(pixels)
    w = len(pixels[0])
    new_h = h * factor
    new_w = w * factor
    out = [[None]*new_w for _ in range(new_h)]
    for y in range(new_h):
        for x in range(new_w):
            out[y][x] = pixels[y // factor][x // factor]
    return out

canvas = create_character()

# 1. Save 1x PNG (64x64)
png_1x = make_png(64, 64, canvas)
open('palo_billar_personaje.png', 'wb').write(png_1x)
open(r'src/main/resources/sprites/palo_billar_personaje.png', 'wb').write(png_1x)

# 2. Save 4x crisp scaled PNG (256x256) for crisp viewing
scaled = scale_pixels(canvas, 4)
png_4x = make_png(256, 256, scaled)
open('palo_billar_personaje_x4.png', 'wb').write(png_4x)
open(r'src/main/resources/sprites/palo_billar_personaje_x4.png', 'wb').write(png_4x)

# 3. Generate LibreSprite JS Script that draws this exact character into LibreSprite's active image!
js_lines = [
    '// Script para LibreSprite: Creacion del Personaje Taco de Billar ("Cuey")',
    'var col = app.pixelColor;',
    'var img = app.activeImage;',
    'if (!img) {',
    '    console.log("No hay ninguna imagen activa. Abre o crea un lienzo de 64x64 y vuelve a ejecutar el script.");',
    '} else {',
    '    console.log("Dibujando personaje Palo de Billar en lienzo de " + img.width + "x" + img.height + "...");'
]

for y in range(64):
    for x in range(64):
        c = canvas[y][x]
        if c[3] > 0: # not transparent
            js_lines.append(f'    img.putPixel({x}, {y}, col.rgba({c[0]}, {c[1]}, {c[2]}, {c[3]}));')

js_lines.append('    if (app.activeSprite) { app.activeSprite.commit(); }')
js_lines.append('    console.log("Personaje Taco de Billar creado exitosamente!");')
js_lines.append('}')

js_code = '\n'.join(js_lines)

# Save JS script in LibreSprite scripts folder
ls_script_path = r'C:\Users\AlumnoFP\AppData\Roaming\LibreSprite\scripts\crear_personaje_taco.js'
open(ls_script_path, 'w', encoding='utf-8').write(js_code)
open('crear_personaje_taco.js', 'w', encoding='utf-8').write(js_code)

print("Character generated and saved to PNG and LibreSprite scripts!")
