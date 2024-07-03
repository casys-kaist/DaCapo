import torch
from typing import List, Tuple


def calculate_accuracy(output, target, topk_list: List[int]) -> Tuple[List[float], torch.Tensor]:
    with torch.no_grad():
        maxk = max(topk_list)
        batch_size = target.size(0)

        _, pred = output.topk(maxk, 1, True, True)
        
        pred = pred.t()
        correct = pred.eq(target.view(1, -1).expand_as(pred))

        res = []
        for k in topk_list:
            correct_k = correct[:k].reshape(-1).float().sum(0, keepdim=True)
            res.append(correct_k.mul_(100.0 / batch_size).item())

        return res, correct.reshape(-1)


class ClassificationAccuracyTracker:
    def __init__(self) -> None:
        self.total_cnt = 0
        self.avg_acc1 = 0
        self.avg_acc5 = 0
        self.corrects = []

    def reset(self):
        self.total_cnt = 0
        self.avg_acc1 = 0
        self.avg_acc5 = 0
        self.corrects = []

    def update(self, acc1, acc5, batch_size):
        self.avg_acc1 = (self.avg_acc1 * self.total_cnt + acc1 * batch_size) / (self.total_cnt + batch_size)
        self.avg_acc5 = (self.avg_acc5 * self.total_cnt + acc5 * batch_size) / (self.total_cnt + batch_size)
        self.total_cnt += batch_size