from PIL import Image

from .preprocess import preprocess_image
from .predict import CLASS_NAMES, predict


def detect_tiles(img: bytes) -> list[str]:
    img, padding_info = preprocess_image(img)
    detection = predict(img, padding_info)
    detection.sort(key=lambda x: x.x1)
    return [CLASS_NAMES[d.class_id] for d in detection]


__all__ = ("detect_tiles",)
