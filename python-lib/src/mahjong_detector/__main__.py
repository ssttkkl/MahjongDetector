from sys import argv
from PIL import Image
from mahjong_detector import detect_tiles


if __name__ == "__main__":
    input_img = Image.open(argv[1])
    result = detect_tiles(input_img)
    print(result)
