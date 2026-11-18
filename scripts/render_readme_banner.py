"""Generate the lightweight animated architecture header used by README.md."""

from pathlib import Path
import math

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "assets" / "cloudresilience-architecture.gif"
WIDTH, HEIGHT = 1200, 440
FRAMES = 30


def load_font(size: int, *, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    windows_fonts = ("C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/segoeui.ttf") if bold else ("C:/Windows/Fonts/segoeui.ttf",)
    linux_fonts = ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",) if bold else ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",)
    candidates = windows_fonts + linux_fonts
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


TITLE = load_font(25, bold=True)
SUBTITLE = load_font(16)
CARD_TITLE = load_font(17, bold=True)
CARD_DETAIL = load_font(13)
FOOTER = load_font(13)


def draw_arrow(draw: ImageDraw.ImageDraw, start: tuple[int, int], end: tuple[int, int], color: str) -> None:
    draw.line((start, end), fill=color, width=3)
    angle = math.atan2(end[1] - start[1], end[0] - start[0])
    tip_length = 12
    left = (end[0] - tip_length * math.cos(angle - math.pi / 6), end[1] - tip_length * math.sin(angle - math.pi / 6))
    right = (end[0] - tip_length * math.cos(angle + math.pi / 6), end[1] - tip_length * math.sin(angle + math.pi / 6))
    draw.polygon((end, left, right), fill=color)


def draw_card(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    title: str,
    detail: str,
    accent: str,
) -> None:
    draw.rounded_rectangle(box, radius=18, fill="#111D30", outline="#26364C", width=2)
    draw.rounded_rectangle((box[0], box[1], box[0] + 7, box[3]), radius=4, fill=accent)
    draw.ellipse((box[0] + 22, box[1] + 25, box[0] + 36, box[1] + 39), fill=accent)
    draw.text((box[0] + 48, box[1] + 22), title, font=CARD_TITLE, fill="#F1F6FC")
    draw.text((box[0] + 23, box[1] + 61), detail, font=CARD_DETAIL, fill="#A9B8CB")


def render_frame(frame: int) -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT), "#0B1422")
    pixels = image.load()
    for y in range(HEIGHT):
        blend = y / (HEIGHT - 1)
        color = (
            round(11 + 5 * blend),
            round(20 + 8 * blend),
            round(34 + 14 * blend),
        )
        for x in range(WIDTH):
            pixels[x, y] = color

    draw = ImageDraw.Draw(image)
    for x in range(28, WIDTH, 38):
        for y in range(24, HEIGHT, 38):
            draw.ellipse((x, y, x + 1, y + 1), fill="#1D2A3D")

    draw.text((54, 35), "CLOUDRESILIENCE", font=TITLE, fill="#F1F6FC")
    draw.text((54, 72), "A secure, resilient e-commerce platform", font=SUBTITLE, fill="#9BAFC5")
    draw.rounded_rectangle((1000, 41, 1142, 72), radius=15, fill="#102D34", outline="#1E6A69", width=1)
    draw.ellipse((1013, 52, 1021, 60), fill="#4AD9B2")
    draw.text((1031, 48), "MODULAR MONOLITH", font=load_font(11, bold=True), fill="#A5E8D8")

    browser = (55, 180, 260, 300)
    api = (345, 180, 580, 300)
    database = (680, 130, 890, 250)
    broker = (965, 130, 1165, 250)
    redis = (440, 345, 650, 407)
    consumers = (965, 298, 1165, 382)

    draw_arrow(draw, (browser[2], 240), (api[0], 240), "#35CDB7")
    draw_arrow(draw, (api[2], 210), (database[0], 190), "#7C9EFF")
    draw_arrow(draw, (database[2], 190), (broker[0], 190), "#E9B96E")
    draw_arrow(draw, (broker[0] + 80, broker[3]), (broker[0] + 80, consumers[1]), "#E9B96E")
    draw.line(((api[0] + 115, api[3]), (api[0] + 115, redis[1])), fill="#A88AF2", width=3)
    draw.line(((api[0] + 115, redis[1]), (redis[0], redis[1])), fill="#A88AF2", width=3)

    draw_card(draw, browser, "React web", "Customer and admin journeys", "#35CDB7")
    draw_card(draw, api, "Spring Boot API", "Orders • inventory • sessions", "#64A3FF")
    draw_card(draw, database, "PostgreSQL", "Durable source of truth", "#8B9CFF")
    draw_card(draw, broker, "RabbitMQ", "At-least-once events", "#E9B96E")
    draw.rounded_rectangle(redis, radius=15, fill="#111D30", outline="#26364C", width=2)
    draw.ellipse((redis[0] + 18, redis[1] + 25, redis[0] + 30, redis[1] + 37), fill="#A88AF2")
    draw.text((redis[0] + 41, redis[1] + 18), "Redis sessions", font=CARD_TITLE, fill="#F1F6FC")
    draw.text((redis[0] + 18, redis[1] + 44), "Shared HTTP sessions only", font=CARD_DETAIL, fill="#A9B8CB")
    draw.rounded_rectangle(consumers, radius=15, fill="#111D30", outline="#26364C", width=2)
    draw.ellipse((consumers[0] + 18, consumers[1] + 23, consumers[0] + 30, consumers[1] + 35), fill="#E9B96E")
    draw.text((consumers[0] + 41, consumers[1] + 15), "Consumers", font=CARD_TITLE, fill="#F1F6FC")
    draw.text((consumers[0] + 18, consumers[1] + 46), "Notifications • activity", font=CARD_DETAIL, fill="#A9B8CB")

    # A single subtle pulse illustrates the asynchronous outbox-to-broker path.
    phase = (frame % FRAMES) / FRAMES
    pulse_x = round(database[2] + (broker[0] - database[2]) * phase)
    pulse_y = 190
    glow = 10 + round(3 * math.sin(frame * 2 * math.pi / FRAMES))
    draw.ellipse((pulse_x - glow, pulse_y - glow, pulse_x + glow, pulse_y + glow), fill="#8A6B2E")
    draw.ellipse((pulse_x - 5, pulse_y - 5, pulse_x + 5, pulse_y + 5), fill="#FFF0BE")

    draw.text((55, 416), "PostgreSQL transaction  →  Outbox  →  Durable broker  →  Idempotent consumers", font=FOOTER, fill="#B4C3D4")
    return image


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    frames = [render_frame(frame).quantize(colors=96, method=Image.Quantize.FASTOCTREE) for frame in range(FRAMES)]
    frames[0].save(
        OUTPUT,
        save_all=True,
        append_images=frames[1:],
        duration=90,
        loop=0,
        optimize=True,
        disposal=2,
    )
    print(f"Wrote {OUTPUT} ({OUTPUT.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
