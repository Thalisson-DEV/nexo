import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, input, OnChanges, output, signal, SimpleChanges, viewChild } from '@angular/core';
import { CesButtonComponent, CesStatusComponent } from '@ces/ui';

@Component({
  selector: 'app-solicitacao-tecnica-assinatura',
  standalone: true,
  imports: [CesButtonComponent, CesStatusComponent],
  templateUrl: './solicitacao-tecnica-assinatura.component.html',
  styleUrl: './solicitacao-tecnica-assinatura.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SolicitacaoTecnicaAssinaturaComponent implements AfterViewInit, OnChanges {
  readonly label = input.required<string>();
  readonly value = input('FISICA');
  readonly valueChanged = output<string>();
  readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  protected readonly captured = signal(true);
  protected readonly physical = signal(true);
  private context: CanvasRenderingContext2D | null = null;
  private drawing = false;
  private viewReady = false;

  ngAfterViewInit(): void { this.viewReady = true; this.prepareCanvas(); }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['value']) return;
    const value = this.value();
    this.physical.set(value === 'FISICA');
    this.captured.set(Boolean(value));
    if (this.viewReady && (value === 'FISICA' || !value)) this.clearCanvas();
  }

  protected pointerDown(event: PointerEvent): void {
    if (this.physical()) return;
    this.prepareCanvas();
    this.drawing = true;
    this.canvas().nativeElement.setPointerCapture(event.pointerId);
    const point = this.point(event);
    this.context?.beginPath(); this.context?.moveTo(point.x, point.y);
  }

  protected pointerMove(event: PointerEvent): void {
    if (!this.drawing || this.physical()) return;
    const point = this.point(event);
    this.context?.lineTo(point.x, point.y); this.context?.stroke(); this.captured.set(true);
  }

  protected pointerUp(): void {
    if (!this.drawing) return;
    this.drawing = false; this.context?.closePath(); this.emitCanvas();
  }

  protected usePhysical(): void {
    this.clearCanvas(); this.physical.set(true); this.captured.set(true); this.valueChanged.emit('FISICA');
  }

  protected useDigital(): void {
    this.physical.set(false); this.captured.set(false); this.valueChanged.emit('');
    this.prepareCanvas();
  }

  protected clear(): void {
    this.physical.set(false); this.captured.set(false); this.clearCanvas(); this.valueChanged.emit('');
  }

  private prepareCanvas(): void {
    const canvas = this.canvas().nativeElement;
    const rect = canvas.getBoundingClientRect();
    const ratio = Math.max(1, window.devicePixelRatio || 1);
    const width = Math.max(1, Math.floor(rect.width * ratio));
    const height = Math.max(1, Math.floor(rect.height * ratio));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    this.context = canvas.getContext('2d');
    this.context?.setTransform(ratio, 0, 0, ratio, 0, 0);
    if (this.context) {
      this.context.strokeStyle = getComputedStyle(canvas).color;
      this.context.lineWidth = 2; this.context.lineCap = 'round'; this.context.lineJoin = 'round';
    }
  }

  private point(event: PointerEvent): { x: number; y: number } {
    const rect = this.canvas().nativeElement.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  }

  private emitCanvas(): void {
    if (this.captured()) this.valueChanged.emit(this.canvas().nativeElement.toDataURL('image/png'));
  }

  private clearCanvas(): void {
    const canvas = this.canvas().nativeElement;
    const ratio = Math.max(1, window.devicePixelRatio || 1);
    this.context?.clearRect(0, 0, canvas.width / ratio, canvas.height / ratio);
  }
}
