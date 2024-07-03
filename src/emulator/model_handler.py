import torch
from tqdm import tqdm
from typing import Tuple
from torch.utils.data import Dataset
from torch.utils.data import Dataset, DataLoader, TensorDataset
import torchvision.transforms as transforms
from bfp.bfp_config import BfpConfig
from bfp.bfp_model_converter import BfpModelConverter
from bfp.bfp_model_precision_changer import BfpModelPrecisionChanger
from emulator.config import Config
from emulator.dataset import CustomSampler
from util.reproduce import seed_worker
from util.model_generator import ModelGenerator
from util.accuracy import calculate_accuracy, ClassificationAccuracyTracker


class ModelPrecision:
    MX9 = 7
    MX6 = 4
    MX4 = 2


class ModelHandler:
    def __init__(self,
                 config: Config,
                 name: str,
                 precision: ModelPrecision,
                 num_classes: int,
                 device: torch.device,
                 weight_path: str = None):
        self.config = config
        self.name = name
        
        self.module = ModelGenerator.generate_model(name=self.name,
                                                    num_classes=num_classes,
                                                    device=device,
                                                    weight_path=weight_path)

        self.device = device

        # set BFP configuration and convert FP32 model to BFP model
        BfpConfig.use_bfp = True
        BfpConfig.bfp_M_Bit = precision
        BfpConfig.group_size = 16
        BfpConfig.use_mx = True
        BfpConfig.apply_single_bfp_tensor = True
        BfpConfig.prec_activation = True
        BfpConfig.prec_weight = True
        BfpConfig.prec_gradient = True

        bfp_converter = BfpModelConverter()
        bfp_converter.convert(self.module, ratio=1.0)

        # move to GPU device
        self.module = self.module.to(self.device)

        self.bfp_precision_changer = BfpModelPrecisionChanger()

    def change_precision(self, precision: ModelPrecision):
        self.bfp_precision_changer.change_precision(self.module, m_bit=precision)

    def train(self,
              train_dataset: Dataset,
              valid_dataset: Dataset,
              epochs: int) -> float:
        if train_dataset == None or epochs == 0:
            return
        
        criterion = torch.nn.CrossEntropyLoss().to(device=self.config.device)

        optimizer = torch.optim.SGD(self.module.parameters(),
                                    lr=self.config.lr,
                                    momentum=self.config.momentum,
                                    weight_decay=self.config.weight_decay)
        
        train_transform = transforms.Compose([
            transforms.RandomHorizontalFlip(),
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406],
                                 std=[0.229, 0.224, 0.225])
        ])

        train_dataset.transform = train_transform

        g = torch.Generator()
        g.manual_seed(self.config.seed)

        data_loader = DataLoader(dataset=train_dataset,
                                 num_workers=self.config.num_workers,
                                 shuffle=True,
                                 pin_memory=True,
                                 batch_size=self.config.train_batch_size,
                                 worker_init_fn=seed_worker,
                                 generator=g)
        
        model = self.module
        model.train()
        for _ in tqdm(range(epochs), desc=f"Retraining",
                      unit=f" epochs, (# of batches: {self.config.train_batch_size})"):
            for inputs, targets, _ in data_loader:
                inputs, targets = inputs.to(self.config.device), targets.to(self.config.device)
                outputs = model(inputs)

                loss = criterion(outputs, targets)

                optimizer.zero_grad()
                loss.backward()
                optimizer.step()

        valid_accuracy_tracker = ClassificationAccuracyTracker()

        if valid_dataset is not None:
            valid_transform = transforms.Compose([
                transforms.Resize((224, 224)),
                transforms.ToTensor(),
                transforms.Normalize(mean=[0.485, 0.456, 0.406],
                                     std=[0.229, 0.224, 0.225])
            ])

            valid_dataset.transform = valid_transform

            valid_data_loader = DataLoader(dataset=valid_dataset,
                                           num_workers=self.config.num_workers,
                                           shuffle=False,
                                           pin_memory=True,
                                           batch_size=self.config.train_batch_size,
                                           worker_init_fn=seed_worker,
                                           generator=g)

            model.eval()
            with torch.no_grad():
                for inputs, targets, _ in tqdm(valid_data_loader,
                                               desc=f"Validation",
                                               unit=f" # of batches: {self.config.train_batch_size})"):
                    inputs, targets = inputs.to(self.config.device), targets.to(self.config.device)
                    outputs = model(inputs)

                    top1_acc, _ = calculate_accuracy(outputs, targets, topk_list=[1])
                    top1_acc = top1_acc[0]
                
                    valid_accuracy_tracker.update(top1_acc, 0, inputs.shape[0])

        return valid_accuracy_tracker.avg_acc1
    
    def infer(self,
              phase: int,
              dataset: Dataset,
              index_slice: Tuple[int, int],
              phase_accuracy_tracker: ClassificationAccuracyTracker,
              window_accuracy_tracker: ClassificationAccuracyTracker):
        criterion = torch.nn.CrossEntropyLoss().to(device=self.config.device)

        g = torch.Generator()
        g.manual_seed(self.config.seed)

        valid_transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406],
                                 std=[0.229, 0.224, 0.225])
        ])

        dataset.transform = valid_transform

        data_loader = DataLoader(dataset=dataset,
                                 num_workers=self.config.num_workers,
                                 sampler=CustomSampler(indices=list(range(index_slice[0],
                                                                          index_slice[1] + 1))),
                                 shuffle=False,
                                 pin_memory=True,
                                 batch_size=self.config.infer_batch_size,
                                 worker_init_fn=seed_worker,
                                 generator=g)
        
        model = self.module
        device = self.config.device

        model.eval()
        num_imgs = 0
        with torch.no_grad():
            for inputs, targets, _ in tqdm(data_loader,
                                           desc=f"Phase #{phase} inference",
                                           unit=" batches (only for emulation)"):
                inputs, targets = inputs.to(device), targets.to(device)
                outputs = model(inputs)

                num_imgs += inputs.shape[0]
                
                top1_acc, top1_corrects = calculate_accuracy(outputs, targets, topk_list=[1])
                top1_acc = top1_acc[0]
                top1_corrects = top1_corrects.cpu().numpy().astype(int).tolist()

                phase_accuracy_tracker.corrects.extend(top1_corrects)
                window_accuracy_tracker.corrects.extend(top1_corrects)

                loss = criterion(outputs, targets).item()

                if phase_accuracy_tracker is not None:
                    phase_accuracy_tracker.update(top1_acc, 0, inputs.shape[0])
                if window_accuracy_tracker is not None:
                    window_accuracy_tracker.update(top1_acc, 0, inputs.shape[0])
    
    def label(self, dataset: Dataset) -> Dataset:
        g = torch.Generator()
        g.manual_seed(self.config.seed)

        data_loader = DataLoader(dataset=dataset,
                                 num_workers=self.config.num_workers,
                                 pin_memory=True,
                                 batch_size=self.config.infer_batch_size,
                                 worker_init_fn=seed_worker,
                                 generator=g)
        
        student_inputs = []
        student_targets = []
        
        self.to_eval()
        with torch.no_grad():
            for inputs, targets, _ in tqdm(data_loader, desc="Data labeling", unit=" frames"):
                inputs = inputs.to(self.device)

                outputs = self.module(inputs).cpu()

                inputs = inputs.cpu()

                batch = inputs.shape[0]
                for b in range(batch):
                    student_inputs.append(inputs[b])
                    output_labels = torch.argmax(targets if batch == 1 else targets[b]).cpu()
                    student_targets.append(output_labels)

        inputs = torch.stack(student_inputs)
        targets = torch.stack(student_targets)
        
        return TensorDataset(inputs, targets)