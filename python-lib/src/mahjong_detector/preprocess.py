from PIL import Image, ImageOps
from typing import Tuple, NamedTuple


class PaddingInfo(NamedTuple):
    scale: float
    pad_x: int
    pad_y: int
    original_height: int
    original_width: int


def preprocess_image(input_image: Image.Image) -> Tuple[Image.Image, PaddingInfo]:
    """
    图像预处理流程：
    1. 转换为灰度图
    2. 对比度拉伸
    3. 等比缩放并填充到640x640
    """
    # 步骤1: 灰度化
    gray_image = convert_to_grayscale(input_image)

    # 步骤2: 对比度拉伸
    contrast_image = stretch_contrast(gray_image)

    # 步骤3: 等比缩放并填充
    padded_image, padding_info = scale_and_pad_to_square(contrast_image, 640, (0, 0, 0))

    return padded_image, padding_info


def convert_to_grayscale(image: Image.Image) -> Image.Image:
    """使用Pillow的convert方法实现灰度化"""
    return image.convert("L")  # 'L'模式表示8位灰度图


def stretch_contrast(image: Image.Image) -> Image.Image:
    """对比度拉伸（直方图均衡化）"""
    return ImageOps.autocontrast(image)


def scale_and_pad_to_square(image: Image.Image, target_size: int, background_color: Tuple[int, int, int]) -> Tuple[Image.Image, PaddingInfo]:
    """
    等比缩放并填充到目标尺寸
    :param image: 输入图像(Pillow Image)
    :param target_size: 目标正方形边长
    :param background_color: 填充背景色(RGB)
    :return: (处理后的图像, PaddingInfo)
    """
    # 计算缩放比例
    original_width, original_height = image.size
    scale = target_size / max(original_width, original_height)

    # 计算缩放后的尺寸
    scaled_width = int(original_width * scale)
    scaled_height = int(original_height * scale)

    # 计算填充位置
    pad_x = (target_size - scaled_width) // 2
    pad_y = (target_size - scaled_height) // 2

    # 缩放图像
    scaled_image = image.resize((scaled_width, scaled_height), Image.BILINEAR)

    # 创建新图像并填充
    if image.mode == "L":  # 灰度图
        squared_image = Image.new("L", (target_size, target_size), color=background_color[0])
    else:  # 彩色图
        squared_image = Image.new("RGB", (target_size, target_size), color=background_color)

    # 粘贴缩放后的图像到中心
    squared_image.paste(scaled_image, (pad_x, pad_y))

    # 返回处理后的图像和填充信息
    padding_info = PaddingInfo(scale=scale, pad_x=pad_x, pad_y=pad_y, original_height=original_height, original_width=original_width)

    return squared_image, padding_info


# 使用示例
if __name__ == "__main__":
    # 加载图像
    input_img = Image.open("input.png")

    # 预处理
    processed_img, padding_info = preprocess_image(input_img)

    # 保存结果
    processed_img.save("output.png")

    # 打印填充信息
    print(f"Scale: {padding_info.scale:.2f}")
    print(f"Padding X: {padding_info.pad_x}, Y: {padding_info.pad_y}")
    print(f"Original size: {padding_info.original_width}x{padding_info.original_height}")
