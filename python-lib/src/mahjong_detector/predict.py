import threading
from pathlib import Path
from typing import List, NamedTuple
import onnxruntime as rt

import numpy as np
from PIL import Image

from .preprocess import PaddingInfo

model_path = Path(__file__).parent / "model.onnx"

CLASS_NAMES = [
    "1m",
    "1p",
    "1s",
    "2m",
    "2p",
    "2s",
    "3m",
    "3p",
    "3s",
    "4m",
    "4p",
    "4s",
    "5m",
    "5p",
    "5s",
    "6m",
    "6p",
    "6s",
    "7m",
    "7p",
    "7s",
    "8m",
    "8p",
    "8s",
    "9m",
    "9p",
    "9s",
    "chun",
    "haku",
    "hatsu",
    "nan",
    "pe",
    "sha",
    "tou",
]


threadlocal = threading.local()


def get_inference_session() -> rt.InferenceSession:
    """为每个线程获取独立的InferenceSession实例（懒初始化）"""
    if not hasattr(threadlocal, "session"):
        threadlocal.session = rt.InferenceSession(model_path)
    return threadlocal.session


class Detection(NamedTuple):
    x1: int
    y1: int
    x2: int
    y2: int
    class_id: int
    confidence: float


def create_input_tensor(img: Image.Image):
    return (
        np.array(img.convert("RGB"), dtype=np.float32).transpose(2, 0, 1)[np.newaxis, ...] / 255.0
    )  # 添加batch维度并归一化


def postprocess(
    output: np.ndarray,
    padding: PaddingInfo,
    num_classes: int,
    conf_threshold: float = 0.5,
    iou_threshold: float = 0.5,
) -> List[Detection]:
    """
    YOLOv8 后处理 (纯NumPy实现)

    参数:
        output: 模型输出数组 [84, 8400]
        padding: 预处理时的填充信息
        num_classes: 类别数
        conf_threshold: 置信度阈值
        iou_threshold: NMS的IoU阈值

    返回:
        List[Detection]: 过滤后的检测结果
    """
    # 1. 检查输出形状
    if output.shape != (84, 8400) and output.shape != (4 + num_classes, 8400):
        raise ValueError(
            f"非法输出格式，预期[84,8400]或[{4 + num_classes},8400]，实际{output.shape}"
        )

    # 2. 提取框数据 (xc, yc, w, h)
    boxes = output[:4].T  # [8400, 4]

    # 3. 提取类别置信度并找到最大置信度类别
    class_confs = output[4 : 4 + num_classes].T  # [8400, num_classes]
    max_conf = np.max(class_confs, axis=1)  # [8400,]
    class_ids = np.argmax(class_confs, axis=1)  # [8400,]

    # 4. 过滤低置信度检测
    mask = max_conf > conf_threshold
    boxes = boxes[mask]
    class_ids = class_ids[mask]
    max_conf = max_conf[mask]

    if len(boxes) == 0:
        return []

    # 5. 转换到原始图像坐标 (补偿填充和缩放)
    xc, yc, w, h = boxes.T  # 分别获取所有框的xc,yc,w,h

    # 计算原始坐标 (一次性向量化计算)
    x1 = ((xc - w / 2 - padding.pad_x) / padding.scale).clip(0, padding.original_width)
    y1 = ((yc - h / 2 - padding.pad_y) / padding.scale).clip(0, padding.original_height)
    x2 = ((xc + w / 2 - padding.pad_x) / padding.scale).clip(0, padding.original_width)
    y2 = ((yc + h / 2 - padding.pad_y) / padding.scale).clip(0, padding.original_height)

    # 转换为整数坐标
    boxes_orig = np.stack([x1, y1, x2, y2], axis=1).astype(int)

    # 6. 应用非极大抑制 (NMS)
    keep_indices = nms(boxes_orig, max_conf, iou_threshold)

    # 7. 构建最终检测结果
    return [
        Detection(
            x1=int(boxes_orig[i, 0]),
            y1=int(boxes_orig[i, 1]),
            x2=int(boxes_orig[i, 2]),
            y2=int(boxes_orig[i, 3]),
            class_id=int(class_ids[i]),
            confidence=float(max_conf[i]),
        )
        for i in keep_indices
    ]


def nms(boxes: np.ndarray, scores: np.ndarray, iou_threshold: float) -> List[int]:
    """
    非极大抑制实现 (纯NumPy)

    参数:
        boxes: [N, 4] (x1,y1,x2,y2)
        scores: [N,]
        iou_threshold: IoU阈值

    返回:
        保留的检测框索引列表
    """
    # 按置信度降序排序
    order = np.argsort(scores)[::-1]
    keep = []

    while order.size > 0:
        i = order[0]
        keep.append(i)

        # 计算当前框与其他框的IoU
        xx1 = np.maximum(boxes[i, 0], boxes[order[1:], 0])
        yy1 = np.maximum(boxes[i, 1], boxes[order[1:], 1])
        xx2 = np.minimum(boxes[i, 2], boxes[order[1:], 2])
        yy2 = np.minimum(boxes[i, 3], boxes[order[1:], 3])

        inter = np.maximum(0.0, xx2 - xx1) * np.maximum(0.0, yy2 - yy1)
        area_i = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
        area_other = (boxes[order[1:], 2] - boxes[order[1:], 0]) * (
            boxes[order[1:], 3] - boxes[order[1:], 1]
        )
        iou = inter / (area_i + area_other - inter)

        # 保留IoU低于阈值的框
        inds = np.where(iou <= iou_threshold)[0]
        order = order[inds + 1]  # +1 因为order[1:]被使用

    return keep


def calculate_iou(box1: np.ndarray, box2: np.ndarray) -> float:
    """
    计算两个框的IoU (辅助函数)
    """
    xx1 = max(box1[0], box2[0])
    yy1 = max(box1[1], box2[1])
    xx2 = min(box1[2], box2[2])
    yy2 = min(box1[3], box2[3])

    inter = max(0, xx2 - xx1) * max(0, yy2 - yy1)
    area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
    area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
    return inter / (area1 + area2 - inter)


def predict(img: Image.Image, padding_info: PaddingInfo) -> list[Detection]:
    session = get_inference_session()

    input_name = session.get_inputs()[0].name
    input_data = create_input_tensor(img)
    outputs = session.run(None, {input_name: input_data})
    detections = postprocess(
        outputs[0][0, ...], padding_info, num_classes=len(CLASS_NAMES)
    )
    return detections
