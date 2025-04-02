package com.example.cs205game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@SuppressLint("ViewConstructor")
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "GameView";
    private GameThread thread;
    private GameManager gameManager;

    // --- Paints ---
    private Paint backgroundPaint;
    private Paint processQueueAreaPaint;
    private Paint processPaint;        // Base paint for normal processes
    private Paint ioProcessPaint;      // Paint for IO processes
    private Paint corePaint;
    private Paint ioAreaPaint;
    private Paint bufferPaint;
    private Paint clientAreaPaint;
    private Paint textPaint;
    private Paint smallTextPaint; // For smaller labels like process ID
    private Paint memoryCellPaint;
    private Paint memoryUsedPaint;
    private Paint healthBarPaint;
    private Paint healthBarBackgroundPaint;
    private Paint patienceGreenPaint;
    private Paint patienceYellowPaint;
    private Paint patienceRedPaint;
    private Paint dropZoneHighlightPaint; // For highlighting valid drop zones
    private Paint errorFeedbackPaint; // For FCFS errors etc.

    // --- Drag and Drop State ---
    private Process draggingProcess = null;
    private PointF dragStartOffset = null;
    private PointF dragCurrentPos = null;
    private int originalSourceCoreId = -1;
    private RectF draggingProcessBounds = new RectF(); // Current visual bounds while dragging

    // --- Layout Rects ---
    private Rect processQueueAreaRect = new Rect();
    private Rect memoryAreaRect = new Rect();
    private Rect scoreHpAreaRect = new Rect();
    private Rect bufferAreaRect = new Rect();
    private Rect clientAreaRect = new Rect();
    private Map<Integer, Rect> coreAreaRects = new HashMap<>();
    private Rect ioAreaRect = new Rect();
    private Map<Integer, Rect> queueProcessRects = new HashMap<>();

    // --- Temporary Rects ---
    private Rect tempRect = new Rect();
    private RectF tempRectF = new RectF();

    // --- Dynamic Layout Values ---
    private float processInQueueHeight = 60f; // Example initial value
    private float processInQueueWidth = 100f;
    private float processOnCoreWidth = 80f;
    private float processOnCoreHeight = 50f;
    private float processInIoWidth = 100f;
    private float processInIoHeight = 60f;


    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);

        gameManager = new GameManager();
        initializePaints();

        Log.i(TAG, "GameView created");
    }

    private void initializePaints() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.DKGRAY);

        processQueueAreaPaint = new Paint();
        processQueueAreaPaint.setColor(Color.parseColor("#444444"));
        processQueueAreaPaint.setStyle(Paint.Style.FILL);

        processPaint = new Paint();
        processPaint.setColor(Color.CYAN);
        processPaint.setStyle(Paint.Style.FILL);

        ioProcessPaint = new Paint(); // Different color for IO processes
        ioProcessPaint.setColor(Color.MAGENTA);
        ioProcessPaint.setStyle(Paint.Style.FILL);

        patienceGreenPaint = new Paint();
        patienceGreenPaint.setColor(Color.GREEN);
        patienceGreenPaint.setStyle(Paint.Style.STROKE);
        patienceGreenPaint.setStrokeWidth(8);

        patienceYellowPaint = new Paint();
        patienceYellowPaint.setColor(Color.YELLOW);
        patienceYellowPaint.setStyle(Paint.Style.STROKE);
        patienceYellowPaint.setStrokeWidth(8);

        patienceRedPaint = new Paint();
        patienceRedPaint.setColor(Color.RED);
        patienceRedPaint.setStyle(Paint.Style.STROKE);
        patienceRedPaint.setStrokeWidth(8);

        corePaint = new Paint();
        corePaint.setColor(Color.parseColor("#6699CC"));
        corePaint.setStyle(Paint.Style.STROKE);
        corePaint.setStrokeWidth(4);

        ioAreaPaint = new Paint();
        ioAreaPaint.setColor(Color.parseColor("#FFB74D"));
        ioAreaPaint.setStyle(Paint.Style.STROKE);
        ioAreaPaint.setStrokeWidth(4);

        bufferPaint = new Paint();
        bufferPaint.setColor(Color.parseColor("#81C784"));
        bufferPaint.setStyle(Paint.Style.STROKE);
        bufferPaint.setStrokeWidth(4);

        clientAreaPaint = new Paint();
        clientAreaPaint.setColor(Color.parseColor("#BA68C8"));
        clientAreaPaint.setStyle(Paint.Style.STROKE);
        clientAreaPaint.setStrokeWidth(4);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);

        smallTextPaint = new Paint();
        smallTextPaint.setColor(Color.BLACK);
        smallTextPaint.setTextSize(20f); // Smaller for IDs inside processes
        smallTextPaint.setAntiAlias(true);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);

        memoryCellPaint = new Paint();
        memoryCellPaint.setColor(Color.GRAY);
        memoryCellPaint.setStyle(Paint.Style.STROKE);
        memoryCellPaint.setStrokeWidth(2);

        memoryUsedPaint = new Paint();
        memoryUsedPaint.setColor(Color.BLUE);
        memoryUsedPaint.setStyle(Paint.Style.FILL);

        healthBarPaint = new Paint();
        healthBarPaint.setColor(Color.GREEN);
        healthBarPaint.setStyle(Paint.Style.FILL);

        healthBarBackgroundPaint = new Paint();
        healthBarBackgroundPaint.setColor(Color.RED);
        healthBarBackgroundPaint.setStyle(Paint.Style.FILL);

        dropZoneHighlightPaint = new Paint();
        dropZoneHighlightPaint.setColor(Color.argb(100, 0, 255, 0)); // Semi-transparent green
        dropZoneHighlightPaint.setStyle(Paint.Style.FILL);

        errorFeedbackPaint = new Paint();
        errorFeedbackPaint.setColor(Color.argb(150, 255, 0, 0)); // Semi-transparent red
        errorFeedbackPaint.setStyle(Paint.Style.FILL);

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.i(TAG, "Surface created");
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();
        gameManager.startGame(); // Start client threads
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "Surface changed: w=" + width + ", h=" + height);
        // Recalculate layout here if it depends on size
        calculateLayoutRects(width, height); 
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.i(TAG, "Surface destroyed");
        boolean retry = true;
        while (retry) {
            try {
                thread.setRunning(false);
                thread.join();
                retry = false;
                Log.i(TAG, "Game thread joined successfully");
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while joining game thread", e);
            }
        }
        gameManager.stopGame(); 
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameManager == null) return false;

        float x = event.getX();
        float y = event.getY();
        int action = event.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                draggingProcess = findDraggableProcessAt(x, y);
                if (draggingProcess != null) {
                    Log.d(TAG, "Started dragging Process " + draggingProcess.getId() + " from state " + draggingProcess.getCurrentState());
                    RectF processRect = getProcessVisualBounds(draggingProcess); // Use RectF now
                    if (processRect != null) {
                        dragStartOffset = new PointF(x - processRect.left, y - processRect.top);
                        dragCurrentPos = new PointF(processRect.left, processRect.top);
                        draggingProcessBounds.set(processRect); // Store initial bounds
                    } else {
                        dragStartOffset = new PointF(20, 20); 
                        dragCurrentPos = new PointF(x - dragStartOffset.x, y - dragStartOffset.y);
                        // Estimate bounds based on type for drawing dragged item
                        float w = (draggingProcess.getCurrentState() == Process.ProcessState.IN_QUEUE) ? processInQueueWidth : processOnCoreWidth;
                        float h = (draggingProcess.getCurrentState() == Process.ProcessState.IN_QUEUE) ? processInQueueHeight : processOnCoreHeight;
                         draggingProcessBounds.set(dragCurrentPos.x, dragCurrentPos.y, dragCurrentPos.x + w, dragCurrentPos.y + h);
                    }
                    if (draggingProcess.getCurrentState() == Process.ProcessState.ON_CORE && draggingProcess instanceof IOProcess) {
                        originalSourceCoreId = findCoreIdForProcess(draggingProcess.getId());
                    }
                     return true; 
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggingProcess != null && dragStartOffset != null && dragCurrentPos != null) {
                    dragCurrentPos.set(x - dragStartOffset.x, y - dragStartOffset.y);
                    // Update the bounds being dragged for drawing
                    draggingProcessBounds.offsetTo(dragCurrentPos.x, dragCurrentPos.y);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (draggingProcess != null) {
                    Log.d(TAG, "Stopped dragging Process " + draggingProcess.getId() + " at (" + x + "," + y + ")");
                    handleDrop(draggingProcess, x, y);
                    draggingProcess = null;
                    dragStartOffset = null;
                    dragCurrentPos = null;
                    originalSourceCoreId = -1;
                    return true;
                }
                break;
        }
        return false;
    }

    
    private Process findDraggableProcessAt(float x, float y) {
        // 1. Check processes in the queue (only the head is draggable)
        Process headProcess = gameManager.getProcessManager().getProcessQueue().peek();
        if (headProcess != null) {
            Rect headRect = queueProcessRects.get(headProcess.getId());
            if (headRect != null && headRect.contains((int) x, (int) y)) {
                 if (gameManager.getProcessManager().isProcessAtHead(headProcess.getId())) {
                    return headProcess;
                 } else { 
                     Log.w(TAG, "Attempted to drag non-head process: " + headProcess.getId());
                     // TODO: Show brief error feedback (e.g., flash red)?
                 }
            }
        }

        // 2. Check processes on cores that require IO interaction
        for (Map.Entry<Integer, Rect> entry : coreAreaRects.entrySet()) {
            Core core = gameManager.getCpuCores().get(entry.getKey());
            Process p = core.getCurrentProcess();
            if (p != null && p instanceof IOProcess) {
                IOProcess ioP = (IOProcess) p;
                if (ioP.isCpuPausedForIO()) {
                    RectF pBounds = getProcessVisualBoundsOnCore(core.getCoreId(), p);
                    if (pBounds != null && pBounds.contains(x, y)) {
                        return p;
                    }
                    else if (entry.getValue().contains((int) x, (int) y)) {
                        Log.w(TAG, "Hit core area for IO process, not specific bounds");
                        return p;
                    }
                }
            }
        }

        // 3. Check process in the IO Area if it's completed IO
        Process pInIO = gameManager.getIoArea().getCurrentProcess();
        if (pInIO != null && pInIO instanceof IOProcess) {
            IOProcess ioP = (IOProcess) pInIO;
            if (ioP.isIoCompleted()) {
                RectF pBounds = getProcessVisualBoundsInIO(pInIO);
                if (pBounds != null && pBounds.contains(x, y)) {
                    return pInIO;
                }
                else if (ioAreaRect.contains((int) x, (int) y)) {
                    Log.w(TAG, "Hit IO area for completed IO process, not specific bounds");
                    return pInIO;
                }
            }
        }

        return null;
    }

    
     private RectF getProcessVisualBounds(Process process) { // Return RectF now
        if (process == null) return null;

        Rect intRect = null;
        RectF floatRect = null;

        // Check queue
        intRect = queueProcessRects.get(process.getId());
        if (intRect != null) return new RectF(intRect);

        // Check cores
        int coreId = findCoreIdForProcess(process.getId());
        if (coreId != -1) {
            return getProcessVisualBoundsOnCore(coreId, process);
        }

        // Check IO Area
        if (gameManager.getIoArea().getCurrentProcess() != null && gameManager.getIoArea().getCurrentProcess().getId() == process.getId()) {
            return getProcessVisualBoundsInIO(process);
        }

        return null; 
     }

    // Helper to calculate visual bounds of a process *drawn on* a specific core
    private RectF getProcessVisualBoundsOnCore(int coreId, Process p) {
        Rect coreRect = coreAreaRects.get(coreId);
        if (coreRect == null || p == null) return null;

        float pWidth = processOnCoreWidth;
        float pHeight = processOnCoreHeight;
        float pLeft = coreRect.centerX() - pWidth / 2;
        float pTop = coreRect.centerY() - pHeight / 2 - 15; // Matches drawing logic
        return new RectF(pLeft, pTop, pLeft + pWidth, pTop + pHeight);
    }

     // Helper to calculate visual bounds of a process *drawn in* the IO Area
     private RectF getProcessVisualBoundsInIO(Process p) {
         if (p == null) return null;
         float pWidth = processInIoWidth;
         float pHeight = processInIoHeight;
         float pLeft = ioAreaRect.centerX() - pWidth / 2;
         float pTop = ioAreaRect.centerY() - pHeight / 2 - 15; // Matches drawing logic
         return new RectF(pLeft, pTop, pLeft + pWidth, pTop + pHeight);
     }

     
     private int findCoreIdForProcess(int processId) {
         for(Core core : gameManager.getCpuCores()) {
             if(core.isUtilized() && core.getCurrentProcess() != null && core.getCurrentProcess().getId() == processId) {
                 return core.getCoreId();
             }
         }
         return -1; 
     }

    
    private void handleDrop(Process droppedProcess, float dropX, float dropY) {
        Process.ProcessState sourceState = droppedProcess.getCurrentState();

        // Check drop target: Cores
        for (Map.Entry<Integer, Rect> entry : coreAreaRects.entrySet()) {
            int coreId = entry.getKey();
            Rect coreRect = entry.getValue();
            if (coreRect.contains((int) dropX, (int) dropY)) {
                Log.d(TAG, "Dropped Process " + droppedProcess.getId() + " onto Core " + coreId);
                if (sourceState == Process.ProcessState.IN_QUEUE) {
                    if (gameManager.getProcessManager().isProcessAtHead(droppedProcess.getId())) {
                        gameManager.moveProcessFromQueueToCore(droppedProcess.getId(), coreId);
                    } else {
                        Log.e(TAG, "FCFS Error on drop - Process " + droppedProcess.getId() + " no longer at head.");
                    }
                } else if (sourceState == Process.ProcessState.IN_IO && droppedProcess instanceof IOProcess) {
                    IOProcess ioP = (IOProcess) droppedProcess;
                    if (ioP.isIoCompleted()) {
                        gameManager.moveProcessFromIOToCore(droppedProcess.getId(), coreId);
                    } else {
                         Log.w(TAG, "Invalid drop onto Core " + coreId + ": IO Process from IO area not completed IO.");
                    }
                } else {
                     Log.w(TAG, "Invalid drop onto Core " + coreId + " from state " + sourceState);
                }
                return;
            }
        }

        // Check drop target: IO Area
        if (ioAreaRect.contains((int) dropX, (int) dropY)) {
             Log.d(TAG, "Dropped Process " + droppedProcess.getId() + " onto IO Area");
             if (sourceState == Process.ProcessState.ON_CORE && droppedProcess instanceof IOProcess) {
                 IOProcess ioP = (IOProcess) droppedProcess;
                 if (ioP.isCpuPausedForIO()) {
                    int sourceCoreId = (originalSourceCoreId != -1) ? originalSourceCoreId : findCoreIdForProcess(droppedProcess.getId());
                    if (sourceCoreId != -1) {
                        gameManager.moveProcessFromCoreToIO(droppedProcess.getId(), sourceCoreId);
                    } else {
                        Log.e(TAG, "Could not find source core for IO process drop?");
                    }
                 } else {
                     Log.w(TAG, "Invalid drop onto IO Area: IOProcess on core is not paused for IO.");
                 }
             } else {
                 Log.w(TAG, "Invalid drop onto IO Area from state " + sourceState + " or not an IOProcess.");
             }
             return;
        }

        Log.d(TAG, "Process " + droppedProcess.getId() + " dropped onto invalid area.");
    }

    public void update(double deltaTime) {
        gameManager.update(deltaTime);
    }

    
    public void drawGame(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        canvas.drawPaint(backgroundPaint);
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Ensure layout rects are calculated
        if (processQueueAreaRect.width() == 0) { // Simple check if not calculated yet
            calculateLayoutRects(width, height);
        }

        // --- Draw Static Layout Elements ---
        canvas.drawRect(processQueueAreaRect, processQueueAreaPaint);
        canvas.drawText("Process Queue", 10, 40, textPaint);
        canvas.drawRect(memoryAreaRect, corePaint); 
        canvas.drawText("Memory", memoryAreaRect.left + 10, memoryAreaRect.top + 40, textPaint);
        canvas.drawRect(bufferAreaRect, bufferPaint);
        canvas.drawText("Buffer", bufferAreaRect.left + 10, bufferAreaRect.top + 40, textPaint);
        canvas.drawRect(clientAreaRect, clientAreaPaint);
        canvas.drawText("Clients", clientAreaRect.left + 10, clientAreaRect.top + 40, textPaint);
        canvas.drawRect(ioAreaRect, ioAreaPaint);
        canvas.drawText("I/O Area", ioAreaRect.left + 10, ioAreaRect.top + 40, textPaint);
        

        // --- Draw Dynamic Elements ---
        drawProcessQueue(canvas, processQueueAreaRect);
        drawScoreHealth(canvas, scoreHpAreaRect);
        drawMemory(canvas, memoryAreaRect);
        drawCores(canvas, coreAreaRects); 
        drawIOArea(canvas, ioAreaRect);
        drawBuffer(canvas, bufferAreaRect);
        drawClientArea(canvas, clientAreaRect);

         // --- Draw Drop Zone Highlights (if dragging) ---
         if (draggingProcess != null) {
             drawDropZoneHighlights(canvas, draggingProcess);
         }

        // --- Draw Dragging Process Last (On Top) ---
        if (draggingProcess != null && dragCurrentPos != null) {
             // Use the stored bounds which are updated during move
            drawProcessRepresentation(canvas, draggingProcess, draggingProcessBounds);
        }
    }

    
    private void calculateLayoutRects(int width, int height) {
        // Adjust layout calculations slightly for better spacing
        int queueWidth = width / 5;
        processQueueAreaRect.set(0, 0, queueWidth, height);

        int mainAreaLeft = queueWidth + 10; // Add small gap
        int topBarHeight = height / 10;
        memoryAreaRect.set(mainAreaLeft, 10, width - 10, topBarHeight);

        int scoreHpHeight = height / 12;
        int scoreHpTop = topBarHeight + 10;
        scoreHpAreaRect.set(mainAreaLeft, scoreHpTop, width - 10, scoreHpTop + scoreHpHeight);

        int midAreaTop = scoreHpTop + scoreHpHeight + 20;
        int midAreaHeight = height / 5;
        int clientWidth = width / 4;
        int bufferRight = width - clientWidth - 20;
        bufferAreaRect.set(mainAreaLeft + 10, midAreaTop, bufferRight, midAreaTop + midAreaHeight);
        clientAreaRect.set(bufferRight + 10, midAreaTop, width - 10, midAreaTop + midAreaHeight);

        int ioHeight = height / 7;
        int ioBottomMargin = 10;
        int ioTop = height - ioHeight - ioBottomMargin;
        ioAreaRect.set(mainAreaLeft + 10, ioTop, width - 10, ioTop + ioHeight);

        int coreAreaTop = midAreaTop + midAreaHeight + 20;
        int coreAreaBottom = ioTop - 20;
        int coreGridWidth = width - mainAreaLeft - 10; // Account for margins
        int coreGridHeight = coreAreaBottom - coreAreaTop;
        int numCoreCols = 2;
        int numCoreRows = 2;
        
        // Calculate size based on available space
        float totalHorizontalPadding = coreGridWidth * 0.15f; // 15% horizontal padding total
        float totalVerticalPadding = coreGridHeight * 0.15f; // 15% vertical padding total
        float coreWidth = (coreGridWidth - totalHorizontalPadding) / numCoreCols;
        float coreHeight = (coreGridHeight - totalVerticalPadding) / numCoreRows;
        float coreXSpacing = totalHorizontalPadding / (numCoreCols + 1);
        float coreYSpacing = totalVerticalPadding / (numCoreRows + 1);

        // Update dynamic process sizes based on core size
        processOnCoreWidth = coreWidth * 0.6f;
        processOnCoreHeight = coreHeight * 0.4f;
        // Keep queue/IO sizes potentially fixed or related to other areas
        processInQueueWidth = processQueueAreaRect.width() - 40;
        processInQueueHeight = (processQueueAreaRect.height() - 80 - (11 * 15)) / 10f; // Approx based on drawing loop
        processInIoWidth = ioAreaRect.width() * 0.3f;
        processInIoHeight = ioAreaRect.height() * 0.6f;


        coreAreaRects.clear();
        for (int i = 0; i < gameManager.getCpuCores().size(); i++) {
            int row = i / numCoreCols;
            int col = i % numCoreCols;
            float left = mainAreaLeft + coreXSpacing + col * (coreWidth + coreXSpacing);
            float top = coreAreaTop + coreYSpacing + row * (coreHeight + coreYSpacing);
            coreAreaRects.put(i, new Rect((int)left, (int)top, (int)(left + coreWidth), (int)(top + coreHeight)));
        }
    }


    private void drawProcessQueue(Canvas canvas, Rect area) {
        Queue<Process> queue = gameManager.getProcessManager().getProcessQueue();
        if (queue == null) return;

        queueProcessRects.clear();

        int maxVisibleProcesses = 10;
        int totalSpacing = (maxVisibleProcesses + 1) * 15;
        int availableHeight = area.height() - 80 - totalSpacing;
        processInQueueHeight = Math.max(20, availableHeight / (float)maxVisibleProcesses);
        processInQueueWidth = area.width() - 40;
        int startY = area.top + 80; 
        int margin = 15;
        int currentY = startY;

        synchronized (queue) {
            int index = 0;
            for (Process p : queue) {
                if (index >= maxVisibleProcesses) break;
                if (p == draggingProcess) {
                     currentY += (int)processInQueueHeight + margin;
                     index++;
                    continue;
                }

                int top = currentY;
                int bottom = currentY + (int)processInQueueHeight;
                int left = area.left + 20;
                int right = left + (int)processInQueueWidth;
                tempRect.set(left, top, right, bottom);
                queueProcessRects.put(p.getId(), new Rect(tempRect)); // Store Rect, not tempRect
                drawProcessRepresentation(canvas, p, new RectF(tempRect)); // Draw using RectF

                currentY += (int)processInQueueHeight + margin;
                index++;
            }
        }
    }

    /** Overload to draw process in a specific RectF */
    private void drawProcessRepresentation(Canvas canvas, Process p, RectF bounds) {
         // Select paint based on process type
         Paint bodyPaint = (p instanceof IOProcess) ? ioProcessPaint : processPaint;
         canvas.drawOval(bounds, bodyPaint);

         // Draw ID Text centered
         String idText = "P" + p.getId();
         canvas.drawText(idText, bounds.centerX(), bounds.centerY() + smallTextPaint.getTextSize() / 3, smallTextPaint);

         // Draw patience arc IF in queue
         if (p.getCurrentState() == Process.ProcessState.IN_QUEUE) {
             drawPatienceArc(canvas, p, bounds);
         }
    }

     /** Overload to draw process at a specific position with specific size (used for dragging) */
    private void drawProcessRepresentation(Canvas canvas, Process p, float x, float y, float width, float height) {
        tempRectF.set(x, y, x + width, y + height);
        drawProcessRepresentation(canvas, p, tempRectF);
    }

    private void drawPatienceArc(Canvas canvas, Process p, RectF processBounds) {
        float sweepAngle = 360f * (float)(p.getPatienceCounter() / p.getInitialPatience());
        Paint patiencePaint;
        double patienceRatio = p.getPatienceCounter() / p.getInitialPatience();

        if (patienceRatio > 0.7) {
            patiencePaint = patienceGreenPaint;
        } else if (patienceRatio > 0.4) {
            patiencePaint = patienceYellowPaint;
        } else {
            patiencePaint = patienceRedPaint;
        }
        float padding = 10f;
        tempRectF.set(processBounds.left - padding, processBounds.top - padding,
                     processBounds.right + padding, processBounds.bottom + padding);
        canvas.drawArc(tempRectF, -90, sweepAngle, false, patiencePaint);
    }

    private void drawScoreHealth(Canvas canvas, Rect area) {
        // Draw Score
        canvas.drawText("Score: " + gameManager.getScore(), area.left + 20, area.centerY() + textPaint.getTextSize()/3, textPaint);

        // Draw Health Bar
        int barMaxHeight = area.height() - 20;
        int barWidth = area.width() / 3;
        int barHeight = Math.min(40, barMaxHeight);
        int barLeft = area.right - barWidth - 20;
        int barTop = area.centerY() - barHeight/2;
        Rect healthBgRect = tempRect;
        healthBgRect.set(barLeft, barTop, barLeft + barWidth, barTop + barHeight);
        float healthRatio = (float) gameManager.getHealth() / GameManager.INITIAL_HEALTH;
        int currentHealthWidth = (int) (barWidth * healthRatio);
        Rect healthFgRect = new Rect(barLeft, barTop, barLeft + currentHealthWidth, barTop + barHeight);

        canvas.drawRect(healthBgRect, healthBarBackgroundPaint);
        canvas.drawRect(healthFgRect, healthBarPaint);
        canvas.drawText("HP", healthBgRect.centerX() - textPaint.measureText("HP")/2, healthBgRect.centerY() + textPaint.getTextSize()/3, textPaint);
    }

    private void drawMemory(Canvas canvas, Rect area) {
        int totalCells = GameManager.MEMORY_CAPACITY;
        int cellsPerRow = 4; // 4x4 grid
        int numRows = 4;

        // Calculate cell size based on the smaller dimension to ensure square-ish cells
        float availableWidth = area.width();
        float availableHeight = area.height();
        float cellWidth = availableWidth / cellsPerRow;
        float cellHeight = availableHeight / numRows;
        float cellSize = Math.min(cellWidth, cellHeight) * 0.8f; // Use 80% and make square
        float totalGridWidth = cellSize * cellsPerRow;
        float totalGridHeight = cellSize * numRows;
        float startX = area.left + (availableWidth - totalGridWidth) / 2;
        float startY = area.top + (availableHeight - totalGridHeight) / 2;

        int usedCells = gameManager.getMemory().getUsedMemory();

        for (int i = 0; i < totalCells; i++) {
            int row = i / cellsPerRow;
            int col = i % cellsPerRow;
            float left = startX + col * cellSize;
            float top = startY + row * cellSize;
            float right = left + cellSize;
            float bottom = top + cellSize;

            if (i < usedCells) {
                 canvas.drawRect(left, top, right, bottom, memoryUsedPaint);
            }
            canvas.drawRect(left, top, right, bottom, memoryCellPaint);
        }
    }

    private void drawCores(Canvas canvas, Map<Integer, Rect> coreRectsMap) {
        for (Map.Entry<Integer, Rect> entry : coreRectsMap.entrySet()) {
            int coreId = entry.getKey();
            Rect coreRect = entry.getValue();
            Core core = gameManager.getCpuCores().get(coreId);

            canvas.drawRect(coreRect, corePaint);
            canvas.drawText("Core " + core.getCoreId(), coreRect.left + 10, coreRect.top + 30, textPaint);

            Process p = core.getCurrentProcess();
            if (core.isUtilized() && p != null) {
                if (p == draggingProcess) continue;

                // Draw process representation centered within the core rect
                 RectF pBounds = getProcessVisualBoundsOnCore(coreId, p);
                 if (pBounds != null) {
                     drawProcessRepresentation(canvas, p, pBounds);

                     // Draw CPU Timer progress below the process
                     float cpuProgressRatio = 1.0f - (float)(p.getRemainingCpuTime() / p.getCpuTimer());
                     int progressWidth = (int)(coreRect.width() * 0.8f);
                     int progressLeft = coreRect.left + (coreRect.width() - progressWidth) / 2;
                     int progressTop = (int)(pBounds.bottom + 5); // Position below process
                     int progressHeight = 20;
                     tempRect.set(progressLeft, progressTop, progressLeft + progressWidth, progressTop + progressHeight);
                     canvas.drawRect(tempRect, memoryCellPaint); // Background
                     tempRect.right = progressLeft + (int)(progressWidth * cpuProgressRatio);
                     canvas.drawRect(tempRect, memoryUsedPaint); // Foreground
                 }

                 // Indicate if waiting for IO
                 if (p instanceof IOProcess) {
                    IOProcess ioP = (IOProcess) p;
                    if (ioP.isCpuPausedForIO()) {
                        canvas.drawText("IO!", coreRect.centerX(), coreRect.bottom - 10, patienceRedPaint);
                    }
                 }
            }
        }
    }

    private void drawIOArea(Canvas canvas, Rect area) {
         IOProcess p = gameManager.getIoArea().getCurrentProcess();
         if (p != null) {
            if (p == draggingProcess) return;

             // Draw process representation centered in IO area
             RectF pBounds = getProcessVisualBoundsInIO(p);
             if (pBounds != null) {
                drawProcessRepresentation(canvas, p, pBounds);

                // Draw IO Timer progress
                 float ioProgressRatio = 1.0f - (float)(p.getRemainingIoTime() / p.getIoTimer());
                 int progressWidth = (int)(area.width() * 0.8f);
                 int progressLeft = area.left + (area.width() - progressWidth) / 2;
                 int progressTop = (int)(pBounds.bottom + 5); // Position below process
                 int progressHeight = 20;
                 tempRect.set(progressLeft, progressTop, progressLeft + progressWidth, progressTop + progressHeight);
                 canvas.drawRect(tempRect, memoryCellPaint); // Background
                 tempRect.right = progressLeft + (int)(progressWidth * ioProgressRatio);
                 canvas.drawRect(tempRect, memoryUsedPaint); // Foreground

                 if (p.isIoCompleted()) {
                     canvas.drawText("DONE", area.centerX(), area.bottom - 10, patienceGreenPaint);
                 }
             }
         }
    }

     private void drawBuffer(Canvas canvas, Rect area) {
         SharedBuffer buffer = gameManager.getSharedBuffer();
         Queue<Process> bufferQueue = buffer.getBufferQueue(); 
         int bufferSize = buffer.getCurrentSize();
         canvas.drawText("Size: " + bufferSize, area.left + 10, area.bottom - 10, textPaint);

         float itemRadius = 20f;
         float startX = area.left + itemRadius * 2;
         float y = area.centerY();
         float spacing = itemRadius * 2.5f;

         synchronized (bufferQueue) { 
             int count = 0;
             for (Process p : bufferQueue) {
                 float currentX = startX + count * spacing;
                 if (currentX + itemRadius > area.right) break; // Don't draw outside bounds
                 // Use RectF version for consistency
                 tempRectF.set(currentX - itemRadius, y - itemRadius, currentX + itemRadius, y + itemRadius);
                 drawProcessRepresentation(canvas, p, tempRectF);
                 count++;
             }
         }
    }

     private void drawClientArea(Canvas canvas, Rect area) {
        canvas.drawText("(Clients working)", area.left + 10, area.centerY(), textPaint);
     }

     /**
      * Draws highlights on valid drop zones based on the currently dragged process.
      */
     private void drawDropZoneHighlights(Canvas canvas, Process dragged) {
         if (dragged == null) return;

         Process.ProcessState sourceState = dragged.getCurrentState();

         // Highlight Cores if dropping from Queue OR returning from IO
         boolean highlightCores = false;
         if (sourceState == Process.ProcessState.IN_QUEUE) {
             highlightCores = true;
         } else if (sourceState == Process.ProcessState.IN_IO && dragged instanceof IOProcess) {
             IOProcess ioP = (IOProcess) dragged; // Cast here
             if (ioP.isIoCompleted()) {
                 highlightCores = true;
             }
         }

         if (highlightCores) {
             for (Map.Entry<Integer, Rect> entry : coreAreaRects.entrySet()) {
                 Core core = gameManager.getCpuCores().get(entry.getKey());
                 if (!core.isUtilized()) {
                      if (sourceState == Process.ProcessState.IN_QUEUE) {
                          if (gameManager.getMemory().hasEnoughMemory(dragged.getMemoryRequirement())) {
                              canvas.drawRect(entry.getValue(), dropZoneHighlightPaint);
                          }
                      } else { // From IO, memory already allocated
                          canvas.drawRect(entry.getValue(), dropZoneHighlightPaint);
                      }
                 }
             }
         }

         // Highlight IO Area if dropping an IO process that needs IO
         if (sourceState == Process.ProcessState.ON_CORE && dragged instanceof IOProcess) {
             IOProcess ioP = (IOProcess) dragged; // Cast here
             if (ioP.isCpuPausedForIO()) {
                if (!gameManager.getIoArea().isBusy()) {
                    canvas.drawRect(ioAreaRect, dropZoneHighlightPaint);
                }
             }
         }
     }


    // --- Game Lifecycle Methods ---
    public void pause() {
        Log.i(TAG, "Pausing game view");
        if (thread != null) {
            thread.setRunning(false);
             gameManager.stopGame(); 
            try {
                thread.join(500); 
            } catch (InterruptedException e) {
                 Log.w(TAG, "Interrupted joining thread on pause");
            }
        }
    }

    public void resume() {
        Log.i(TAG, "Resuming game view");
        if (thread == null || thread.getState() == Thread.State.TERMINATED) {
            Log.i(TAG, "Creating new game thread");
             thread = new GameThread(getHolder(), this);
        }
        if (!thread.isAlive()) {
             thread.setRunning(true);
             thread.start();
             gameManager.startGame(); 
        }
    }

} 