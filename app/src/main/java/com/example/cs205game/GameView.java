package com.example.cs205game;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.AttributeSet;
import android.app.Activity;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@SuppressLint("ViewConstructor")
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "GameView";
    private GameThread thread;
    private GameManager gameManager;
    private SharedBuffer sharedBuffer;

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
    private Paint memoryTextPaint; // Paint for memory text on processes
    private Paint clientIdlePaint;
    private Paint clientBusyPaint;
    private final Paint queueAreaPaint;
    private final Paint labelPaint;
    private final Paint scorePaint;
    private final Paint scoreBackgroundPaint;
    private final Paint whiteLabelPaint; 

    // --- Game Over State ---
    private boolean isGameOver = false;
    private RectF retryButtonRect = new RectF();
    private RectF quitButtonRect = new RectF();
    private Paint gameOverOverlayPaint;
    private Paint gameOverPanelPaint;
    private Paint gameOverTextPaint;
    private Paint buttonPaint;
    private Paint buttonTextPaint;

    // --- Health Animation State ---
    private int lastHealth = -1; // Initial value to force first animation
    private boolean isHealthAnimating = false;
    private long healthAnimationStartTime = 0;
    private static final long HEALTH_ANIMATION_DURATION_MS = 1000; // Animation lasts 1 second
    private Paint healthFlashPaint;
    private boolean isScreenFlashing = false;
    private Paint screenFlashPaint;
    private static final int SIGNIFICANT_HEALTH_DROP = 10; // Health drop that triggers screen flash
    
    // --- Damage Indicator ---
    private boolean showDamageIndicator = false;
    private String damageText = "";
    private float damageTextX, damageTextY;
    private Paint damageTextPaint;
    private long damageIndicatorStartTime = 0;
    private static final long DAMAGE_INDICATOR_DURATION_MS = 1500; // 1.5 seconds

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
    private RectF bufferArea = new RectF();
    private RectF clientArea = new RectF();
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

    // --- Animation Values ---
    private float animationValue = 0f;
    private long lastTime = System.currentTimeMillis();
    private ValueAnimator animator;

    // Add these paint declarations in the initialization section
    private Paint processBgPaint;
    private Paint ioBgPaint;
    private Paint patienceBgPaint;
    private Paint patienceArcPaint;
    private Paint patienceCenterPaint;
    private Paint textPaint2;

    // --- Error Display State ---
    private boolean isShowingError = false;
    private String errorMessage = "";
    private long errorDisplayStartTime;
    private final Paint errorMessagePaint;

    // --- Pause Button State ---
    private boolean isPaused = false;
    private RectF pauseButtonRect = new RectF();
    private Paint pauseButtonPaint;
    private Paint pauseButtonTextPaint;
    private Paint pauseOverlayPaint;
    private Paint pauseMenuPaint;
    private RectF resumeButtonRect = new RectF();
    private RectF quitFromPauseRect = new RectF();

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        setFocusable(true);

        // Initialize GameManager with context
        this.gameManager = new GameManager(context);
        this.sharedBuffer = gameManager.getSharedBuffer();
        initializePaints();

        queueAreaPaint = new Paint();
        queueAreaPaint.setStyle(Paint.Style.STROKE);
        queueAreaPaint.setColor(Color.LTGRAY);
        queueAreaPaint.setStrokeWidth(4f);
        
        labelPaint = new Paint();
        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(40f);
        labelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        scorePaint = new Paint();
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(50f);
        scorePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        
        scoreBackgroundPaint = new Paint();
        scoreBackgroundPaint.setColor(Color.rgb(50, 50, 50));
        scoreBackgroundPaint.setStyle(Paint.Style.FILL);

        whiteLabelPaint = new Paint();
        whiteLabelPaint.setColor(Color.WHITE);
        whiteLabelPaint.setTextSize(40f); // Same size as labelPaint
        whiteLabelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); // Same style

        // Initialize error message paint
        errorMessagePaint = new Paint();
        errorMessagePaint.setColor(Color.RED);
        errorMessagePaint.setTextSize(30);
        errorMessagePaint.setTextAlign(Paint.Align.CENTER);
        errorMessagePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Log.i(TAG, "GameView created");

        // Game Over Paints
        gameOverOverlayPaint = new Paint();
        gameOverOverlayPaint.setColor(Color.argb(220, 0, 0, 0)); // Darker semi-transparent black

        gameOverPanelPaint = new Paint();
        gameOverPanelPaint.setColor(Color.parseColor("#212121")); // Very dark gray
        gameOverPanelPaint.setStyle(Paint.Style.FILL);

        gameOverTextPaint = new Paint();
        gameOverTextPaint.setColor(Color.parseColor("#F44336")); // Material red
        gameOverTextPaint.setTextSize(100f);
        gameOverTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        gameOverTextPaint.setTextAlign(Paint.Align.CENTER);

        buttonPaint = new Paint();
        buttonPaint.setColor(Color.parseColor("#37474F")); // Dark blue-gray
        buttonPaint.setStyle(Paint.Style.FILL);

        buttonTextPaint = new Paint();
        buttonTextPaint.setColor(Color.parseColor("#4CAF50")); // Green
        buttonTextPaint.setTextSize(50f);
        buttonTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        buttonTextPaint.setTextAlign(Paint.Align.CENTER);
        
        // Start animation loop for visual effects
        startAnimationLoop();

        // Initialize process representation paints
        processBgPaint = new Paint();
        processBgPaint.setColor(Color.parseColor("#3F51B5")); // Indigo for regular processes
        processBgPaint.setStyle(Paint.Style.FILL);
        processBgPaint.setAntiAlias(true);
        
        ioBgPaint = new Paint();
        ioBgPaint.setColor(Color.parseColor("#673AB7")); // Deep Purple for IO processes (changed from teal)
        ioBgPaint.setStyle(Paint.Style.FILL);
        ioBgPaint.setAntiAlias(true);
        
        patienceBgPaint = new Paint();
        patienceBgPaint.setColor(Color.parseColor("#424242")); // Dark gray
        patienceBgPaint.setStyle(Paint.Style.FILL);
        patienceBgPaint.setAntiAlias(true);
        
        patienceArcPaint = new Paint();
        patienceArcPaint.setColor(Color.parseColor("#4CAF50")); // Green
        patienceArcPaint.setStyle(Paint.Style.STROKE);
        patienceArcPaint.setStrokeWidth(4);
        patienceArcPaint.setAntiAlias(true);
        
        patienceCenterPaint = new Paint();
        patienceCenterPaint.setColor(Color.WHITE);
        patienceCenterPaint.setStyle(Paint.Style.FILL);
        patienceCenterPaint.setAntiAlias(true);
        
        textPaint2 = new Paint(textPaint);
        textPaint2.setTextSize(18);

        // Health flash paint for animations
        healthFlashPaint = new Paint();
        healthFlashPaint.setColor(Color.RED);
        healthFlashPaint.setStyle(Paint.Style.FILL);
        healthFlashPaint.setAlpha(0); // Start transparent

        // Screen flash for significant health drops
        screenFlashPaint = new Paint();
        screenFlashPaint.setColor(Color.RED);
        screenFlashPaint.setStyle(Paint.Style.FILL);
        screenFlashPaint.setAlpha(0); // Start transparent
        
        // Damage indicator text
        damageTextPaint = new Paint();
        damageTextPaint.setColor(Color.RED);
        damageTextPaint.setTextSize(40);
        damageTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        damageTextPaint.setTextAlign(Paint.Align.CENTER);
        damageTextPaint.setAlpha(255);

        // Initialize game state
        isGameOver = false;

        // Pause button paints
        pauseButtonPaint = new Paint();
        pauseButtonPaint.setColor(Color.parseColor("#303F9F")); // Indigo
        pauseButtonPaint.setStyle(Paint.Style.FILL);
        
        pauseButtonTextPaint = new Paint();
        pauseButtonTextPaint.setColor(Color.WHITE);
        pauseButtonTextPaint.setTextSize(30f);
        pauseButtonTextPaint.setTextAlign(Paint.Align.CENTER);
        pauseButtonTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        
        pauseOverlayPaint = new Paint();
        pauseOverlayPaint.setColor(Color.BLACK);
        pauseOverlayPaint.setAlpha(150); // Semi-transparent overlay
        
        pauseMenuPaint = new Paint();
        pauseMenuPaint.setColor(Color.parseColor("#212121")); // Dark gray
        pauseMenuPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Initializes and starts the animation loop for visual effects.
     * Animation is much more subtle and only used for very specific elements now.
     */
    private void startAnimationLoop() {
        // Set a fixed animation value instead of animating
        animationValue = 0.75f; // Fixed value that doesn't change
        
        // Still define animator for compatibility but don't make it animate
        animator = ValueAnimator.ofFloat(animationValue, animationValue);
        animator.setDuration(1000);
        animator.setRepeatCount(0);
        
        // This setup keeps the animation value at a constant 0.75f 
        // so elements don't blink or pulse
    }

    private void initializePaints() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.parseColor("#121212")); // Dark theme background

        processQueueAreaPaint = new Paint();
        processQueueAreaPaint.setColor(Color.parseColor("#1E1E1E")); // Dark gray
        processQueueAreaPaint.setStyle(Paint.Style.FILL);

        processPaint = new Paint();
        processPaint.setColor(Color.parseColor("#3949AB")); // Indigo for standard processes
        processPaint.setStyle(Paint.Style.FILL);

        ioProcessPaint = new Paint(); // Different color for IO processes
        ioProcessPaint.setColor(Color.parseColor("#673AB7")); // Deep purple for IO processes (changed from teal)
        ioProcessPaint.setStyle(Paint.Style.FILL);

        patienceGreenPaint = new Paint();
        patienceGreenPaint.setColor(Color.parseColor("#4CAF50")); // Green
        patienceGreenPaint.setStyle(Paint.Style.STROKE);
        patienceGreenPaint.setStrokeWidth(8);

        patienceYellowPaint = new Paint();
        patienceYellowPaint.setColor(Color.parseColor("#FFC107")); // Yellow
        patienceYellowPaint.setStyle(Paint.Style.STROKE);
        patienceYellowPaint.setStrokeWidth(8);

        patienceRedPaint = new Paint();
        patienceRedPaint.setColor(Color.parseColor("#F44336")); // Red
        patienceRedPaint.setStyle(Paint.Style.STROKE);
        patienceRedPaint.setStrokeWidth(8);

        corePaint = new Paint();
        corePaint.setColor(Color.parseColor("#303F9F")); // Dark blue for CPU cores
        corePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        corePaint.setStrokeWidth(4);

        ioAreaPaint = new Paint();
        ioAreaPaint.setColor(Color.parseColor("#512DA8")); // Dark purple for IO area (changed from teal)
        ioAreaPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        ioAreaPaint.setStrokeWidth(4);

        bufferPaint = new Paint();
        bufferPaint.setColor(Color.parseColor("#5E35B1")); // Deep purple for buffer
        bufferPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        bufferPaint.setStrokeWidth(4);

        clientAreaPaint = new Paint();
        clientAreaPaint.setColor(Color.parseColor("#C62828")); // Dark red for client area
        clientAreaPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        clientAreaPaint.setStrokeWidth(4);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30f);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.MONOSPACE); // Computer font style

        smallTextPaint = new Paint();
        smallTextPaint.setColor(Color.WHITE);
        smallTextPaint.setTextSize(20f);
        smallTextPaint.setAntiAlias(true);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        smallTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        memoryCellPaint = new Paint();
        memoryCellPaint.setColor(Color.parseColor("#424242")); // Dark gray
        memoryCellPaint.setStyle(Paint.Style.STROKE);
        memoryCellPaint.setStrokeWidth(2);

        memoryUsedPaint = new Paint();
        memoryUsedPaint.setColor(Color.parseColor("#F44336")); // Red for memory usage (changed from blue)
        memoryUsedPaint.setStyle(Paint.Style.FILL);

        healthBarPaint = new Paint();
        healthBarPaint.setColor(Color.parseColor("#4CAF50")); // Green for health
        healthBarPaint.setStyle(Paint.Style.FILL);

        healthBarBackgroundPaint = new Paint();
        healthBarBackgroundPaint.setColor(Color.parseColor("#424242")); // Dark gray
        healthBarBackgroundPaint.setStyle(Paint.Style.FILL);

        dropZoneHighlightPaint = new Paint();
        dropZoneHighlightPaint.setColor(Color.argb(100, 76, 175, 80)); // Semi-transparent green
        dropZoneHighlightPaint.setStyle(Paint.Style.FILL);

        errorFeedbackPaint = new Paint();
        errorFeedbackPaint.setColor(Color.argb(150, 244, 67, 54)); // Semi-transparent red
        errorFeedbackPaint.setStyle(Paint.Style.FILL);

        memoryTextPaint = new Paint();
        memoryTextPaint.setColor(Color.WHITE);
        memoryTextPaint.setTextSize(24f);
        memoryTextPaint.setAntiAlias(true);
        memoryTextPaint.setTextAlign(Paint.Align.CENTER);
        memoryTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        clientIdlePaint = new Paint();
        clientIdlePaint.setColor(Color.parseColor("#424242")); // Dark gray
        clientIdlePaint.setStyle(Paint.Style.FILL);

        clientBusyPaint = new Paint();
        clientBusyPaint.setColor(Color.parseColor("#4CAF50")); // Green when busy
        clientBusyPaint.setStyle(Paint.Style.FILL);
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
        int x = (int) event.getX();
        int y = (int) event.getY();
        int action = event.getAction();

        // Handle game over UI actions
        if (isGameOver) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (retryButtonRect.contains(x, y)) {
                    // Retry button pressed
                    // Reset the game state
                    ((Activity) getContext()).recreate();
                    return true;
                } else if (quitButtonRect.contains(x, y)) {
                    // Quit button pressed
                    // Return to title screen
                    ((Activity) getContext()).finish();
                    return true;
                }
            }
            return true; // Consume all touches in game over state
        }
        
        // Handle pause button and pause overlay actions
        if (action == MotionEvent.ACTION_DOWN) {
            // Check if pause button was clicked
            if (pauseButtonRect.contains(x, y)) {
                isPaused = !isPaused;
                if (isPaused) {
                    // Pause the game
                    if (thread != null) {
                        thread.setRunning(false);
                    }
                    } else {
                    // Resume the game
                    if (thread != null && !thread.isAlive()) {
                        thread = new GameThread(getHolder(), this);
                        thread.setRunning(true);
                        thread.start();
                    } else if (thread != null) {
                        thread.setRunning(true);
                    }
                }
                invalidate(); // Redraw to show pause/resume state
                return true;
            }
            
            // Handle pause menu buttons when paused
            if (isPaused) {
                // Check if resume button was clicked
                if (resumeButtonRect.contains(x, y)) {
                    isPaused = false;
                    // Resume the game
                    if (thread != null && !thread.isAlive()) {
                        thread = new GameThread(getHolder(), this);
                        thread.setRunning(true);
                        thread.start();
                    } else if (thread != null) {
                        thread.setRunning(true);
                    }
                    invalidate();
                    return true;
                }
                
                // Check if quit button was clicked
                if (quitFromPauseRect.contains(x, y)) {
                    // Return to the title screen
                    ((Activity) getContext()).finish();
                    return true;
                }
                
                // Don't process other touch events when paused
                return true;
            }
        }

        // Handle drag and drop operations (only when not paused)
        return handleDragAndDrop(event);
    }
    
    /**
     * Handles drag and drop operations for processes
     */
    private boolean handleDragAndDrop(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
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

    public void update(double deltaTime) {
        // Don't update game state when paused
        if (isPaused) {
                return;
        }
        
        if (!isGameOver) { // Only update game logic if not game over
            gameManager.update(deltaTime);
            // Check if game over condition is met *after* updating game state
            if (!gameManager.isGameRunning()) {
                isGameOver = true;
                Log.i(TAG, "Game Over condition detected in GameView update.");
            }
        }
    }

    
    public void drawGame(Canvas canvas) {
        if (canvas == null) return;
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Draw background
        canvas.drawPaint(backgroundPaint);

        // Ensure layout rects are calculated
        if (processQueueAreaRect.width() == 0) { // Simple check if not calculated yet
            calculateLayoutRects(width, height);
        }

        // --- Draw Static Layout Elements ---
        canvas.drawRect(memoryAreaRect, corePaint); 
        canvas.drawRect(bufferAreaRect, bufferPaint);
        canvas.drawRect(clientAreaRect, clientAreaPaint);
        canvas.drawRect(ioAreaRect, ioAreaPaint);
        

        // --- Draw Dynamic Elements ---
        drawProcessQueue(canvas);
        drawScoreHealth(canvas, scoreHpAreaRect);
        drawMemory(canvas, memoryAreaRect);
        drawCores(canvas, coreAreaRects); 
        drawIOArea(canvas, ioAreaRect);
        drawBuffer(canvas);
        drawClientArea(canvas);

         // --- Draw Drop Zone Highlights (if dragging) ---
         if (draggingProcess != null) {
             drawDropZoneHighlights(canvas, draggingProcess);
         }

        // --- Draw Dragging Process Last (On Top) ---
        if (draggingProcess != null && dragCurrentPos != null) {
             // Use the stored bounds which are updated during move
            drawProcessRepresentation(canvas, draggingProcess, draggingProcessBounds, animationValue, draggingProcess.getCurrentState() == Process.ProcessState.IN_QUEUE);
        }
        
        // --- Draw error message if needed ---
        if (isShowingError) {
            drawErrorMessage(canvas);
        }

        // --- Draw Game Over Overlay (if applicable) ---
        if (isGameOver) {
            drawGameOverOverlay(canvas);
        }
    }

    private void drawGameOverOverlay(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        // Draw semi-transparent overlay covering the whole screen
        canvas.drawRect(0, 0, width, height, gameOverOverlayPaint);

        // Define panel dimensions (centered)
        float panelWidth = width * 0.6f;
        float panelHeight = height * 0.4f;
        float panelLeft = (width - panelWidth) / 2f;
        float panelTop = (height - panelHeight) / 2f;
        RectF panelRect = new RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);

        // Draw panel background
        canvas.drawRoundRect(panelRect, 20f, 20f, gameOverPanelPaint); // Rounded corners

        // Draw "GAME OVER" text
        float textX = panelRect.centerX();
        float textY = panelRect.top + panelHeight * 0.3f; // Position towards the top
        canvas.drawText("GAME OVER", textX, textY, gameOverTextPaint);

        // Define button dimensions
        float buttonWidth = panelWidth * 0.35f; // Slightly smaller buttons
        float buttonHeight = panelHeight * 0.2f;
        float buttonSpacing = panelWidth * 0.1f; // Increase spacing factor if needed, or adjust calculation below
        float totalButtonWidth = buttonWidth * 2;
        float totalSpacingNeeded = panelWidth - totalButtonWidth; // Total space available for spacing
        float spaceBetweenButtons = totalSpacingNeeded * 0.4f; // Use 40% of available space for gap between buttons
        float spaceOnSides = (totalSpacingNeeded - spaceBetweenButtons) / 2f; // Distribute remaining space to sides

        float buttonY = panelRect.top + panelHeight * 0.6f; // Position lower down

        // Calculate button positions with increased spacing
        float retryLeft = panelRect.left + spaceOnSides;
        retryButtonRect.set(retryLeft, buttonY, retryLeft + buttonWidth, buttonY + buttonHeight);

        float quitLeft = retryLeft + buttonWidth + spaceBetweenButtons;
        quitButtonRect.set(quitLeft, buttonY, quitLeft + buttonWidth, buttonY + buttonHeight);

        // Draw buttons
        canvas.drawRoundRect(retryButtonRect, 10f, 10f, buttonPaint);
        canvas.drawRoundRect(quitButtonRect, 10f, 10f, buttonPaint);

        // Draw button text (centered)
        float buttonTextY = retryButtonRect.centerY() + buttonTextPaint.getTextSize() / 3f;
        canvas.drawText("Retry", retryButtonRect.centerX(), buttonTextY, buttonTextPaint);
        canvas.drawText("Quit to Title", quitButtonRect.centerX(), buttonTextY, buttonTextPaint); // Updated text
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

        // Update RectF versions of the areas
        bufferArea.set(bufferAreaRect);
        clientArea.set(clientAreaRect);
    }


    private void drawProcessQueue(Canvas canvas) {
        // Define queue area dimensions
        float queueLeft = 80;  // Increased margin from left
        float queueTop = 120;  // Increased margin from top
        float queueWidth = getWidth() * 0.15f;
        float queueHeight = getHeight() * 0.7f;
        
        // Draw queue area border
        RectF queueArea = new RectF(queueLeft, queueTop, queueLeft + queueWidth, queueTop + queueHeight);
        canvas.drawRect(queueArea, queueAreaPaint);
        
        // Draw "Process Queue" label above the border using white paint
        canvas.drawText("Process Queue", queueLeft, queueTop - 20, whiteLabelPaint);
        
        // Clear previous process rectangles
        queueProcessRects.clear();
        
        Queue<Process> queue = gameManager.getProcessManager().getProcessQueue();
        if (queue == null) return;
        
        // Calculate process dimensions
        int maxVisibleProcesses = 10;
        float processMargin = 15;
        float availableHeight = queueHeight - processMargin * (maxVisibleProcesses + 1);
        float processHeight = Math.max(20, availableHeight / maxVisibleProcesses);
        float processWidth = queueWidth - 40;
        
        // Start drawing processes
        float currentY = queueTop + processMargin;
        
        synchronized (queue) {
            int index = 0;
            for (Process p : queue) {
                if (index >= maxVisibleProcesses) break;
                if (p == draggingProcess) {
                    currentY += processHeight + processMargin;
                    index++;
                    continue;
                }
                
                RectF processRect = new RectF(
                    queueLeft + 20,
                    currentY,
                    queueLeft + 20 + processWidth,
                    currentY + processHeight
                );
                
                // Store rectangle for touch detection
                queueProcessRects.put(p.getId(), new Rect(
                    (int)processRect.left,
                    (int)processRect.top,
                    (int)processRect.right,
                    (int)processRect.bottom
                ));
                
                // Draw the process
                drawProcessRepresentation(canvas, p, processRect, animationValue, p.getCurrentState() == Process.ProcessState.IN_QUEUE);
                
                currentY += processHeight + processMargin;
                index++;
            }
        }
    }

    private void drawMemoryStatus(Canvas canvas) {
        // Draw memory status in top-right corner with black bold text
        String memText = String.format("MEM: %d / %d GB", 
            gameManager.getMemory().getUsedMemory(),
            gameManager.getMemory().getCapacity());
        
        float memX = getWidth() - 300;  // Fixed position from right
        float memY = 60;  // Fixed Y position near top
        
        // Draw memory text
        canvas.drawText(memText, memX, memY, labelPaint);
    }

    private void drawScore(Canvas canvas) {
        String scoreText = "Score: " + gameManager.getScore();
        float scoreX = 300;  // Fixed position from left
        float scoreY = 60;   // Aligned with memory text
        
        // Draw score background
        float padding = 20;
        float textWidth = scorePaint.measureText(scoreText);
        RectF scoreBackground = new RectF(
            scoreX - padding,
            scoreY - 40,
            scoreX + textWidth + padding,
            scoreY + 10
        );
        
        // Draw background with slight transparency
        scoreBackgroundPaint.setAlpha(200);
        canvas.drawRoundRect(scoreBackground, 15, 15, scoreBackgroundPaint);
        
        // Draw score text
        canvas.drawText(scoreText, scoreX, scoreY, scorePaint);
    }

    /** Overload to draw process in a specific RectF */
    private void drawProcessRepresentation(Canvas canvas, Process p, RectF bounds, float alpha, boolean inQueue) {
        boolean isIOProcess = p instanceof IOProcess;
        
        // Draw chip background
        Paint chipPaint = isIOProcess ? ioBgPaint : processBgPaint;
        chipPaint.setAlpha((int)(255 * alpha));
        canvas.drawRoundRect(bounds, 16, 16, chipPaint);
        
        // For IO processes, just use a distinctive color without any circuit pattern
        
        // Draw process details
        String processText = "P" + p.getId() + " [" + p.getMemoryRequirement() + "M]";
        Paint textPaint = new Paint(this.textPaint);
        textPaint.setAlpha((int)(255 * alpha));
        textPaint.setTextAlign(Paint.Align.LEFT);
        float textX = bounds.left + 12;
        float textY = bounds.centerY() + 6;
        canvas.drawText(processText, textX, textY, textPaint);
        
        // Add specific I/O marker
        if (isIOProcess) {
            Paint ioPaint = new Paint(textPaint2);
            ioPaint.setAlpha((int)(255 * alpha));
            ioPaint.setTextAlign(Paint.Align.RIGHT);
            ioPaint.setColor(Color.parseColor("#FFC107"));  // Amber color for I/O text
            ioPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            float ioLabelX = bounds.right - 12;
            canvas.drawText("I/O", ioLabelX, textY, ioPaint);
            
            // Add IO WAIT text if the process is paused for IO
            IOProcess ioP = (IOProcess) p;
            if (ioP.getCurrentState() == Process.ProcessState.ON_CORE && ioP.isCpuPausedForIO()) {
                // Draw "IO WAIT" text inside the process
                Paint ioPausedPaint = new Paint(patienceRedPaint);
                ioPausedPaint.setStyle(Paint.Style.FILL);
                ioPausedPaint.setTextSize(22); // Smaller text
                ioPausedPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                ioPausedPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("IO WAIT", bounds.centerX(), bounds.centerY() + 8, ioPausedPaint);
            }
        }

        // Draw patience indicator as a decreasing border if in queue
        if (inQueue) {
            // Calculate patience ratio
            float patienceRatio = (float) p.getRemainingPatienceRatio();
            
            // Select paint color based on patience level
            int borderColor;
            if (patienceRatio > 0.7) {
                borderColor = Color.parseColor("#4CAF50"); // Green
            } else if (patienceRatio > 0.4) {
                borderColor = Color.parseColor("#FFC107"); // Yellow
            } else {
                borderColor = Color.parseColor("#F44336"); // Red
            }
            
            // Create border paint
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(borderColor);
            borderPaint.setStrokeWidth(4); // Increased from 3 to 4 for better visibility
            borderPaint.setAlpha((int)(255 * alpha));
            
            // Draw a rectangular border that decreases based on patience
            float cornerRadius = 16; // Same as the roundRect cornerRadius
            
            if (patienceRatio >= 1.0f) {
                // Draw full border for full patience
                canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, borderPaint);
            } else {
                // Calculate the total perimeter
                float width = bounds.width();
                float height = bounds.height();
                float perimeter = 2 * (width + height);
                
                // Calculate how much of the perimeter to draw based on patience
                float remainingPerimeter = perimeter * patienceRatio;
                
                // Create a path to draw the partial perimeter
                Path borderPath = new Path();
                
                // Start from top-left corner
                borderPath.moveTo(bounds.left + cornerRadius, bounds.top);
                
                // Calculate side lengths (adjusted for corners)
                float topSide = width - 2 * cornerRadius;
                float rightSide = height - 2 * cornerRadius;
                float bottomSide = width - 2 * cornerRadius;
                float leftSide = height - 2 * cornerRadius;
                
                // Draw top side if needed
                if (remainingPerimeter > 0) {
                    float drawLength = Math.min(remainingPerimeter, topSide);
                    borderPath.rLineTo(drawLength, 0);
                    remainingPerimeter -= drawLength;
                    
                    // If we've exhausted the patience, draw up to this point and stop
                    if (remainingPerimeter <= 0) {
                        canvas.drawPath(borderPath, borderPaint);
                        return;
                    }
                    
                    // Otherwise, complete the top side
                    if (drawLength < topSide) {
                        borderPath.rLineTo(topSide - drawLength, 0);
                    }
                }
                
                // Draw top-right corner arc
                RectF cornerArc = new RectF(
                    bounds.right - 2 * cornerRadius, 
                    bounds.top, 
                    bounds.right, 
                    bounds.top + 2 * cornerRadius
                );
                borderPath.arcTo(cornerArc, 270, 90);
                
                // Draw right side if needed
                if (remainingPerimeter > 0) {
                    float drawLength = Math.min(remainingPerimeter, rightSide);
                    borderPath.rLineTo(0, drawLength);
                    remainingPerimeter -= drawLength;
                    
                    if (remainingPerimeter <= 0) {
                        canvas.drawPath(borderPath, borderPaint);
                        return;
                    }
                    
                    if (drawLength < rightSide) {
                        borderPath.rLineTo(0, rightSide - drawLength);
                    }
                }
                
                // Draw bottom-right corner arc
                cornerArc = new RectF(
                    bounds.right - 2 * cornerRadius, 
                    bounds.bottom - 2 * cornerRadius, 
                    bounds.right, 
                    bounds.bottom
                );
                borderPath.arcTo(cornerArc, 0, 90);
                
                // Draw bottom side if needed
                if (remainingPerimeter > 0) {
                    float drawLength = Math.min(remainingPerimeter, bottomSide);
                    borderPath.rLineTo(-drawLength, 0);
                    remainingPerimeter -= drawLength;
                    
                    if (remainingPerimeter <= 0) {
                        canvas.drawPath(borderPath, borderPaint);
                        return;
                    }
                    
                    if (drawLength < bottomSide) {
                        borderPath.rLineTo(-(bottomSide - drawLength), 0);
                    }
                }
                
                // Draw bottom-left corner arc
                cornerArc = new RectF(
                    bounds.left, 
                    bounds.bottom - 2 * cornerRadius, 
                    bounds.left + 2 * cornerRadius, 
                    bounds.bottom
                );
                borderPath.arcTo(cornerArc, 90, 90);
                
                // Draw left side if needed
                if (remainingPerimeter > 0) {
                    float drawLength = Math.min(remainingPerimeter, leftSide);
                    borderPath.rLineTo(0, -drawLength);
                    remainingPerimeter -= drawLength;
                    
                    if (remainingPerimeter <= 0) {
                        canvas.drawPath(borderPath, borderPaint);
                        return;
                    }
                    
                    if (drawLength < leftSide) {
                        borderPath.rLineTo(0, -(leftSide - drawLength));
                    }
                }
                
                // Draw top-left corner arc
                cornerArc = new RectF(
                    bounds.left, 
                    bounds.top, 
                    bounds.left + 2 * cornerRadius, 
                    bounds.top + 2 * cornerRadius
                );
                borderPath.arcTo(cornerArc, 180, 90);
                
                // Draw the path
                canvas.drawPath(borderPath, borderPaint);
            }
         }
    }

     /** Overload to draw process at a specific position with specific size (used for dragging) */
    private void drawProcessRepresentation(Canvas canvas, Process p, float x, float y, float width, float height) {
        tempRectF.set(x, y, x + width, y + height);
        drawProcessRepresentation(canvas, p, tempRectF, animationValue, p.getCurrentState() == Process.ProcessState.IN_QUEUE);
    }

    private void drawPatienceArc(Canvas canvas, Process p, RectF circleBounds) {
        // Calculate the patience ratio
        float patienceRatio = (float) p.getRemainingPatienceRatio();
        float sweepAngle = 360f * patienceRatio;
        
        // Select paint based on patience level
        Paint patiencePaint;

        if (patienceRatio > 0.7) {
            patiencePaint = patienceGreenPaint;
        } else if (patienceRatio > 0.4) {
            patiencePaint = patienceYellowPaint;
        } else {
            patiencePaint = patienceRedPaint;
        }
        
        // Draw background circle
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#424242")); // Dark gray
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(circleBounds.centerX(), circleBounds.centerY(), 
                        circleBounds.width()/2, bgPaint);
        
        // Create a RectF for the arc
        RectF arcRect = new RectF(circleBounds);
        
        // Draw the arc representing remaining patience
        patiencePaint.setStyle(Paint.Style.FILL);
        canvas.drawArc(arcRect, -90, sweepAngle, true, patiencePaint);
        
        // Draw a small circular border
        Paint borderPaint = new Paint(patiencePaint);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2);
        canvas.drawCircle(circleBounds.centerX(), circleBounds.centerY(), 
                        circleBounds.width()/2, borderPaint);
        
        // Add a non-blinking outline for critical patience
        if (patienceRatio < 0.3) {
            Paint warningPaint = new Paint(patienceRedPaint);
            warningPaint.setStyle(Paint.Style.STROKE);
            warningPaint.setStrokeWidth(3);
            float warningRadius = circleBounds.width()/2 * 1.2f;
            canvas.drawCircle(circleBounds.centerX(), circleBounds.centerY(), 
                            warningRadius, warningPaint);
        }
    }

    private void drawScoreHealth(Canvas canvas, Rect area) {
        // Check if health has changed
        int currentHealth = gameManager.getHealth();
        if (lastHealth == -1) {
            // First time - just initialize
            lastHealth = currentHealth;
        } else if (currentHealth < lastHealth) {
            // Calculate health lost
            int healthLost = lastHealth - currentHealth;
            
            // Health decreased - start animation
            isHealthAnimating = true;
            healthAnimationStartTime = System.currentTimeMillis();
            
            // Create damage indicator
            showDamageIndicator = true;
            damageText = "-" + healthLost + " HP";
            damageTextX = area.right - 60;
            damageTextY = area.centerY();
            damageIndicatorStartTime = System.currentTimeMillis();
            
            // If health drop is significant, also flash the screen
            if (healthLost >= SIGNIFICANT_HEALTH_DROP) {
                isScreenFlashing = true;
            }
        }
        lastHealth = currentHealth;
        
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
        
        // Apply flashing animation if health is decreasing
        if (isHealthAnimating) {
            long elapsedTime = System.currentTimeMillis() - healthAnimationStartTime;
            
            if (elapsedTime < HEALTH_ANIMATION_DURATION_MS) {
                // Calculate pulsation effect with sine wave - oscillates from 0 to 180 opacity
                float normalized = (float) elapsedTime / HEALTH_ANIMATION_DURATION_MS;
                float pulseIntensity = (float) Math.abs(Math.sin(normalized * Math.PI * 6)); // Animate 3 full cycles
                int alpha = (int) (180 * pulseIntensity);
                
                // Apply flash over the health bar
                healthFlashPaint.setAlpha(alpha);
                canvas.drawRect(healthBgRect, healthFlashPaint);
                
                // Update screen flash if active
                if (isScreenFlashing) {
                    // Screen flash is less intense and fades out faster
                    int screenAlpha = (int) (100 * pulseIntensity * (1.0f - normalized)); // Fades out over time
                    screenFlashPaint.setAlpha(screenAlpha);
                }
                
                // Keep animation going by repainting
                invalidate();
            } else {
                // Animation complete
                isHealthAnimating = false;
                isScreenFlashing = false;
                healthFlashPaint.setAlpha(0);
                screenFlashPaint.setAlpha(0);
            }
        }
        
        canvas.drawText("HP", healthBgRect.centerX() - labelPaint.measureText("HP")/2, 
                       healthBgRect.centerY() + labelPaint.getTextSize()/3, labelPaint);
    }

    private void drawMemory(Canvas canvas, Rect area) {
        int totalCells = GameManager.MEMORY_CAPACITY;
        int cellsPerRow = 4; // 4x4 grid
        int numRows = 4;

        // Calculate cell size based on the drawing area
        float cellWidth = (float)area.width() / cellsPerRow;
        float cellHeight = (float)area.height() / numRows;
        // No extra margins, fill the whole area
        float startX = area.left;
        float startY = area.top;

        int usedCells = gameManager.getMemory().getUsedMemory();

        for (int i = 0; i < totalCells; i++) {
            int row = i / cellsPerRow;
            int col = i % cellsPerRow;
            float left = startX + col * cellWidth;
            float top = startY + row * cellHeight;
            float right = left + cellWidth;
            float bottom = top + cellHeight;

            if (i < usedCells) {
                 canvas.drawRect(left, top, right, bottom, memoryUsedPaint);
            }
            // Draw border slightly inset for better visibility
            canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, memoryCellPaint);
        }

        // Optionally draw total used/available text somewhere nearby
        String memUsageText = "MEM: " + usedCells + " / " + totalCells + " GB";
        float textWidth = textPaint.measureText(memUsageText);
        // Draw text below the grid, centered horizontally within the memory area
        canvas.drawText(memUsageText, area.centerX() - textWidth / 2, area.bottom + textPaint.getTextSize() + 5, textPaint);
    }

    private void drawCores(Canvas canvas, Map<Integer, Rect> coreRectsMap) {
        for (Map.Entry<Integer, Rect> entry : coreRectsMap.entrySet()) {
            int coreId = entry.getKey();
            Rect coreRect = entry.getValue();
            Core core = gameManager.getCpuCores().get(coreId);

            // Draw CPU core background
            RectF coreRectF = new RectF(coreRect);
            canvas.drawRoundRect(coreRectF, 16, 16, corePaint);
            
            // Draw inner CPU area with circuit-like patterns
            float margin = 15;
            RectF innerRect = new RectF(
                coreRectF.left + margin,
                coreRectF.top + margin,
                coreRectF.right - margin,
                coreRectF.bottom - margin
            );
            
            Paint cpuPaint = new Paint();
            cpuPaint.setColor(Color.DKGRAY);
            canvas.drawRoundRect(innerRect, 8, 8, cpuPaint);
            
            // Draw circuit traces
            Paint tracePaint = new Paint();
            tracePaint.setColor(Color.parseColor("#4CAF50")); // Green traces
            tracePaint.setStrokeWidth(2);
            
            // Horizontal traces
            for (int i = 1; i < 4; i++) {
                float y = innerRect.top + innerRect.height() * (i / 4f);
                canvas.drawLine(innerRect.left + 10, y, innerRect.right - 10, y, tracePaint);
            }
            
            // Vertical traces
            for (int i = 1; i < 4; i++) {
                float x = innerRect.left + innerRect.width() * (i / 4f);
                canvas.drawLine(x, innerRect.top + 10, x, innerRect.bottom - 10, tracePaint);
            }
            
            // Draw core label
            canvas.drawText("Core " + core.getId(), coreRect.left + 20, coreRect.top + 30, textPaint);

            // Draw the process if present
            Process p = core.getCurrentProcess();
            if (core.isUtilized() && p != null) {
                if (p == draggingProcess) continue;

                // Draw process representation
                 RectF pBounds = getProcessVisualBoundsOnCore(core.getId(), p);
                 if (pBounds != null) {
                    drawProcessRepresentation(canvas, p, pBounds, animationValue, p.getCurrentState() == Process.ProcessState.IN_QUEUE);

                     // Draw CPU Timer progress below the process
                     float cpuProgressRatio = 1.0f - (float)(p.getRemainingCpuTime() / p.getCpuTimer());
                     int progressWidth = (int)(coreRect.width() * 0.8f);
                     int progressLeft = coreRect.left + (coreRect.width() - progressWidth) / 2;
                     int progressTop = (int)(pBounds.bottom + 5); // Position below process
                     int progressHeight = 20;
                    
                    // Draw progress background
                     tempRect.set(progressLeft, progressTop, progressLeft + progressWidth, progressTop + progressHeight);
                    Paint progressBgPaint = new Paint(memoryCellPaint);
                    progressBgPaint.setStyle(Paint.Style.FILL);
                    progressBgPaint.setColor(Color.parseColor("#424242")); // Dark gray
                    canvas.drawRect(tempRect, progressBgPaint);
                    
                    // Draw progress foreground
                     tempRect.right = progressLeft + (int)(progressWidth * cpuProgressRatio);
                    Paint progressFgPaint = new Paint(memoryUsedPaint);
                    // Color gets greener as it completes
                    float hue = cpuProgressRatio * 120; // 0 is red, 120 is green
                    progressFgPaint.setColor(Color.HSVToColor(new float[]{hue, 0.7f, 0.8f}));
                    canvas.drawRect(tempRect, progressFgPaint);
                    
                    // Add processing activity indicator if active (steady, not pulsing)
                    if (core.isUtilized()) {
                        // Draw activity light
                        Paint activityPaint = new Paint();
                        activityPaint.setColor(Color.parseColor("#F44336")); // Red activity light
                        
                        float lightRadius = 8;
                        canvas.drawCircle(innerRect.right - 20, innerRect.top + 20, lightRadius, activityPaint);
                    }

                    // Draw the downward arrow for IO processes waiting for IO
                 if (p instanceof IOProcess) {
                    IOProcess ioP = (IOProcess) p;
                    if (ioP.isCpuPausedForIO()) {
                            // Draw arrow pointing downward
                            Paint arrowPaint = new Paint();
                            arrowPaint.setColor(Color.parseColor("#FF5252")); // Bright red
                            arrowPaint.setStyle(Paint.Style.FILL);
                            arrowPaint.setStrokeWidth(4);
                            
                            // Draw vertical arrow pointing down
                            float arrowStartX = pBounds.centerX();
                            float arrowStartY = pBounds.bottom + progressHeight + 10;
                            float arrowEndX = arrowStartX;
                            float arrowEndY = arrowStartY + 30;
                            
                            canvas.drawLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY, arrowPaint);
                            
                            // Draw arrow head
                            Path arrowHead = new Path();
                            arrowHead.moveTo(arrowEndX, arrowEndY);
                            arrowHead.lineTo(arrowEndX - 10, arrowEndY - 15);
                            arrowHead.lineTo(arrowEndX + 10, arrowEndY - 15);
                            arrowHead.close();
                            canvas.drawPath(arrowHead, arrowPaint);
                            
                            // Add text label below the arrow
                            Paint labelPaint = new Paint();
                            labelPaint.setColor(Color.parseColor("#FF5252"));
                            labelPaint.setTextSize(16);
                            labelPaint.setTextAlign(Paint.Align.CENTER);
                            labelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                            canvas.drawText("Move to I/O", arrowEndX, arrowEndY + 20, labelPaint);
                        }
                    }
                 }
            }
        }
    }

    private void drawIOArea(Canvas canvas, Rect area) {
         // Draw IO Area label first, shifted down slightly
         float labelY = area.top + whiteLabelPaint.getTextSize() + 10; // Use text size for positioning
         canvas.drawText("I/O Area", area.left + 10, labelY, whiteLabelPaint);

         IOProcess p = gameManager.getIoArea().getCurrentProcess();
         if (p != null) {
            if (p == draggingProcess) return;

             // Draw process representation centered in IO area
             RectF pBounds = getProcessVisualBoundsInIO(p);
             if (pBounds != null) {
                drawProcessRepresentation(canvas, p, pBounds, animationValue, p.getCurrentState() == Process.ProcessState.IN_QUEUE);

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
                     
                     // Add attention indicator for completed IO, but without blinking
                     // Draw upward arrow indicating it should move back to CPU
                     Paint arrowPaint = new Paint();
                     arrowPaint.setColor(Color.parseColor("#4CAF50")); // Green
                     arrowPaint.setStyle(Paint.Style.FILL);
                     arrowPaint.setStrokeWidth(4);
                     
                     // Draw arrow body
                     float arrowStartX = area.centerX();
                     float arrowStartY = area.top - 40;
                     float arrowEndX = area.centerX();
                     float arrowEndY = area.top - 10;
                     
                     canvas.drawLine(arrowStartX, arrowStartY, arrowEndX, arrowEndY, arrowPaint);
                     
                     // Draw arrow head
                     Path arrowHead = new Path();
                     arrowHead.moveTo(arrowEndX, arrowEndY);
                     arrowHead.lineTo(arrowEndX - 10, arrowEndY + 15);
                     arrowHead.lineTo(arrowEndX + 10, arrowEndY + 15);
                     arrowHead.close();
                     canvas.drawPath(arrowHead, arrowPaint);
                     
                     // Add text label
                     Paint labelPaint = new Paint();
                     labelPaint.setColor(Color.parseColor("#4CAF50"));
                     labelPaint.setTextSize(18);
                     labelPaint.setTextAlign(Paint.Align.CENTER);
                     labelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                     canvas.drawText("Return to CPU", arrowStartX, arrowStartY - 10, labelPaint);
                     
                     // Draw highlight around the process (steady, not pulsing)
                     Paint glowPaint = new Paint();
                     glowPaint.setStyle(Paint.Style.STROKE);
                     glowPaint.setColor(Color.parseColor("#4CAF50"));
                     glowPaint.setStrokeWidth(4);
                     RectF glowBounds = new RectF(pBounds);
                     glowBounds.inset(-6, -6);
                     canvas.drawRoundRect(glowBounds, 16, 16, glowPaint);
                 }
             }
         }
    }

    /**
     * Draws the buffer area with improved visualization.
     * Shows processes in a stack-like representation with data flow indicators.
     */
     private void drawBuffer(Canvas canvas) {
        // Get the processes in the buffer using SharedBuffer's methods
        Process[] bufferProcesses = gameManager.getSharedBuffer().getProcessesInBuffer();
        int bufferSize = gameManager.getSharedBuffer().size();
        int capacity = gameManager.getSharedBuffer().getCapacity();
        
        // Draw the buffer container with tech-inspired design
        Paint bufferBorderPaint = new Paint(bufferPaint);
        bufferBorderPaint.setStyle(Paint.Style.STROKE);
        bufferBorderPaint.setStrokeWidth(4);
        bufferBorderPaint.setColor(Color.parseColor("#7B1FA2")); // Deep purple
        
        // Draw a circuit-inspired background
        Paint circuitBgPaint = new Paint();
        circuitBgPaint.setColor(Color.parseColor("#311B92")); // Darker purple
        circuitBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(bufferAreaRect, circuitBgPaint);
        
        // Draw subtle circuit patterns in the background
        Paint circuitPaint = new Paint();
        circuitPaint.setColor(Color.parseColor("#9575CD")); // Light purple
        circuitPaint.setStrokeWidth(1);
        circuitPaint.setStyle(Paint.Style.STROKE);
        circuitPaint.setAlpha(60); // Very subtle
        
        // Draw horizontal and vertical circuit lines
        for (int i = 1; i < 8; i++) {
            float xPos = bufferAreaRect.left + (bufferAreaRect.width() * i / 8f);
            float yPos = bufferAreaRect.top + (bufferAreaRect.height() * i / 8f);
            
            // Horizontal line
            canvas.drawLine(bufferAreaRect.left, yPos, bufferAreaRect.right, yPos, circuitPaint);
            
            // Vertical line
            canvas.drawLine(xPos, bufferAreaRect.top, xPos, bufferAreaRect.bottom, circuitPaint);
            
            // Add small "nodes" at intersections
            if (i % 2 == 0) {
                for (int j = 1; j < 8; j += 2) {
                    float nodeX = bufferAreaRect.left + (bufferAreaRect.width() * i / 8f);
                    float nodeY = bufferAreaRect.top + (bufferAreaRect.height() * j / 8f);
                    canvas.drawCircle(nodeX, nodeY, 2, circuitPaint);
                }
            }
        }
        
        // Draw capacity indicator along the left side
        float capacityHeight = bufferAreaRect.height() - 40; // Leave some margin
        float capacityWidth = 20; // Width of capacity indicator
        float capacityX = bufferAreaRect.left + 20; // Position from left edge
        float capacityY = bufferAreaRect.top + 20; // Position from top
        
        // Draw capacity background
        Paint capacityBgPaint = new Paint();
        capacityBgPaint.setColor(Color.parseColor("#424242")); // Dark gray
        capacityBgPaint.setStyle(Paint.Style.FILL);
        RectF capacityRect = new RectF(
            capacityX, 
            capacityY, 
            capacityX + capacityWidth, 
            capacityY + capacityHeight
        );
        canvas.drawRect(capacityRect, capacityBgPaint);
        
        // Draw fill level
        if (capacity > 0) {
            float fillHeight = (float) bufferSize / capacity * capacityHeight;
            Paint fillPaint = new Paint();
            fillPaint.setColor(Color.parseColor("#7C4DFF")); // Bright purple
            fillPaint.setStyle(Paint.Style.FILL);
            
            RectF fillRect = new RectF(
                capacityX, 
                capacityY + capacityHeight - fillHeight, 
                capacityX + capacityWidth, 
                capacityY + capacityHeight
            );
            canvas.drawRect(fillRect, fillPaint);
            
            // Add level markings
            Paint markingPaint = new Paint();
            markingPaint.setColor(Color.WHITE);
            markingPaint.setStrokeWidth(1);
            for (int i = 0; i <= capacity; i++) {
                float y = capacityY + capacityHeight - (i * capacityHeight / capacity);
                canvas.drawLine(capacityX - 5, y, capacityX, y, markingPaint);
            }
        }
        
        // Draw buffer title
        Paint titlePaint = new Paint(textPaint);
        titlePaint.setTextSize(28);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTypeface(Typeface.MONOSPACE);
        String title = "$ Buffer";
        canvas.drawText(title, bufferAreaRect.left + 80, bufferAreaRect.top + 35, titlePaint);
        
        // Draw capacity text
        String capacityText = "Capacity: " + bufferSize + " / " + capacity;
        canvas.drawText(capacityText, bufferAreaRect.left + 250, bufferAreaRect.top + 35, titlePaint);
        
        // If buffer is empty, show a placeholder message
        if (bufferProcesses.length == 0) {
            String emptyText = "[ Buffer Empty ]";
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTextSize(24);
            titlePaint.setColor(Color.parseColor("#BBBBBB")); // Light gray
            canvas.drawText(emptyText, bufferAreaRect.centerX(), bufferAreaRect.centerY(), titlePaint);
            return;
        }
        
        // Calculate space for process chips
        float processWidth = Math.min(120, (bufferAreaRect.width() - 150) / capacity);
        float processHeight = 60;
        float processSpacing = 10;
        float startX = bufferAreaRect.left + 80;
        float startY = bufferAreaRect.top + 60;
        
        // Draw processes in stack-like representation
        for (int i = 0; i < bufferProcesses.length; i++) {
            Process p = bufferProcesses[i];
            
            // Calculate position in a grid layout (3 or 4 per row depending on size)
            int row = i / 3;
            int col = i % 3;
            
            float x = startX + col * (processWidth + processSpacing);
            float y = startY + row * (processHeight + processSpacing);
            
            // Create a rect for the process
            RectF processRect = new RectF(x, y, x + processWidth, y + processHeight);
            
            // Draw memory chip-like representation
            Paint chipPaint = new Paint();
            chipPaint.setStyle(Paint.Style.FILL);
            
            // Determine color based on cooldown progress
            float cooldownProgress = (float) p.getBufferCooldownProgress();
            if (cooldownProgress >= 1.0f) {
                // Ready for consumption - bright color
                chipPaint.setColor(Color.parseColor("#7C4DFF")); // Bright purple
                } else {
                // Still cooling down - darker color
                chipPaint.setColor(Color.parseColor("#5E35B1")); // Darker purple
            }
            
            // Draw the main chip
            canvas.drawRoundRect(processRect, 8, 8, chipPaint);
            
            // Draw connector pins on the bottom of the chip
            Paint pinPaint = new Paint();
            pinPaint.setColor(Color.parseColor("#E0E0E0")); // Light gray
            pinPaint.setStyle(Paint.Style.FILL);
            
            float pinWidth = 8;
            float pinHeight = 5;
            float pinSpacing = (processWidth - (5 * pinWidth)) / 6;
            float pinY = processRect.bottom;
            
            for (int pin = 0; pin < 5; pin++) {
                float pinX = processRect.left + pinSpacing + pin * (pinWidth + pinSpacing);
                RectF pinRect = new RectF(pinX, pinY, pinX + pinWidth, pinY + pinHeight);
                canvas.drawRect(pinRect, pinPaint);
            }
            
            // Draw a notch on top of the chip (like a real IC chip)
            RectF notchRect = new RectF(
                processRect.centerX() - 10,
                processRect.top - 3,
                processRect.centerX() + 10,
                processRect.top + 3
            );
            canvas.drawRoundRect(notchRect, 3, 3, pinPaint);

                // Draw process ID
                Paint idPaint = new Paint(textPaint);
            idPaint.setTextSize(20);
            idPaint.setColor(Color.WHITE);
            idPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("P" + p.getId(), processRect.centerX(), processRect.centerY() + 7, idPaint);
            
            // Draw cooldown progress bar if still cooling down
            if (cooldownProgress < 1.0f) {
                // Draw cooldown progress
                RectF cooldownRect = new RectF(
                    processRect.left,
                    processRect.bottom - 10,
                    processRect.left + processRect.width() * cooldownProgress,
                    processRect.bottom - 5
                );
                Paint cooldownPaint = new Paint();
                cooldownPaint.setColor(Color.parseColor("#7C4DFF")); // Bright purple
                cooldownPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(cooldownRect, cooldownPaint);
            } else {
                // Draw a "READY" label for processes ready for consumption
                Paint readyPaint = new Paint();
                readyPaint.setColor(Color.parseColor("#69F0AE")); // Bright green
                readyPaint.setTextSize(14);
                readyPaint.setTextAlign(Paint.Align.CENTER);
                readyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                canvas.drawText("READY", processRect.centerX(), processRect.bottom - 15, readyPaint);
            }
        }
        
        // Draw connection to client area
        drawBufferToClientConnection(canvas, bufferProcesses.length == 0);
    }
    
    /**
     * Draws connection lines between buffer and client area with data flow indicators
     */
    private void drawBufferToClientConnection(Canvas canvas, boolean isEmpty) {
        // Draw the connection pipe between buffer and client
        Paint pipePaint = new Paint();
        pipePaint.setStyle(Paint.Style.STROKE);
        pipePaint.setStrokeWidth(3);
        
        // If the buffer is empty, use a faded color
        if (isEmpty) {
            pipePaint.setColor(Color.parseColor("#424242")); // Dark gray for empty
            pipePaint.setAlpha(80);
        } else {
            pipePaint.setColor(Color.parseColor("#7C4DFF")); // Purple for active
        }
        
        // Calculate connection points
        float bufferRight = bufferAreaRect.right;
        float clientLeft = clientAreaRect.left;
        float arrowY = (bufferAreaRect.bottom + bufferAreaRect.top) / 2;
        
        // Draw the main connection line
        canvas.drawLine(bufferRight, arrowY, clientLeft, arrowY, pipePaint);
        
        // Only draw flow indicators if buffer is not empty
        if (!isEmpty) {
            // Draw arrowhead
            Path arrowHead = new Path();
            float arrowHeadSize = 10;
            float arrowHeadX = clientLeft - 5;
            
            arrowHead.moveTo(arrowHeadX, arrowY);
            arrowHead.lineTo(arrowHeadX - arrowHeadSize, arrowY - arrowHeadSize/2);
            arrowHead.lineTo(arrowHeadX - arrowHeadSize, arrowY + arrowHeadSize/2);
            arrowHead.close();
            
            Paint arrowPaint = new Paint(pipePaint);
            arrowPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(arrowHead, arrowPaint);
            
            // Draw data flow dots
            Paint dataPaint = new Paint();
            dataPaint.setColor(Color.parseColor("#B388FF")); // Light purple
            dataPaint.setStyle(Paint.Style.FILL);
            
            float connectionLength = clientLeft - bufferRight;
            int numDots = 5;
            float dotSpacing = connectionLength / (numDots + 1);
            
            // Calculate a fixed offset based on time but not animating
            // This makes it look more stable without the blinking
            float dotOffset = (System.currentTimeMillis() % 1000) / 1000f * dotSpacing;
            
            for (int i = 0; i < numDots; i++) {
                float dotX = bufferRight + dotSpacing * (i + 1) - dotOffset;
                if (dotX < bufferRight) dotX += connectionLength;
                if (dotX > clientLeft) dotX -= dotSpacing;
                
                // Vary dot size slightly based on position
                float dotSize = 3 + (i % 2);
                canvas.drawCircle(dotX, arrowY, dotSize, dataPaint);
            }
            
            // Draw "DATA ►" label above the connection
            Paint labelPaint = new Paint();
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(18);
            labelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            float labelX = bufferRight + (clientLeft - bufferRight) / 2 - 30;
            float labelY = arrowY - 15;
            canvas.drawText("DATA ►", labelX, labelY, labelPaint);
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

    /**
     * Draws a terminal-like system console at the bottom of the screen.
     */
    private void drawSystemConsole(Canvas canvas, int width, int height) {
        float consoleHeight = 40;
        RectF consoleRect = new RectF(0, height - consoleHeight, width, height);
        
        // Draw console background
        Paint consoleBgPaint = new Paint();
        consoleBgPaint.setColor(Color.BLACK);
        canvas.drawRect(consoleRect, consoleBgPaint);
        
        // Draw terminal text
        Paint consoleTextPaint = new Paint();
        consoleTextPaint.setColor(Color.parseColor("#4CAF50")); // Terminal green
        consoleTextPaint.setTypeface(Typeface.MONOSPACE);
        consoleTextPaint.setTextSize(16);
        
        // Current time
        long time = System.currentTimeMillis();
        String timeString = String.format("[%d]", time / 1000);
        
        // System status
        String statusString = String.format("SYSTEM: %s", 
                                         gameManager.getHealth() > 30 ? "ONLINE" : "WARNING");
        
        // Processes info
        String processString = String.format("PROC: %d", 
                                          gameManager.getProcessManager().getProcessQueue().size());
        
        // Memory info
        String memoryString = String.format("MEM: %d/%dGB", 
                                         gameManager.getMemory().getUsedMemory(),
                                         gameManager.getMemory().getCapacity());
        
        // Combine and draw
        String consoleText = String.format("%s %s %s %s", 
                                        timeString, statusString, processString, memoryString);
        canvas.drawText(consoleText, 10, height - 15, consoleTextPaint);
        
        // Add static cursor instead of blinking
        canvas.drawText("_", 10 + consoleTextPaint.measureText(consoleText + " "), 
                        height - 15, consoleTextPaint);
    }
    
    /**
     * Draws the pause button in the top-right corner
     */
    private void drawPauseButton(Canvas canvas) {
        // Use fixed position and size rather than relative to processQueueAreaRect
        int buttonSize = 90;
        float screenWidth = getWidth();
        float screenHeight = getHeight();
        
        // Position in top right of I/O area
        float buttonX = ioAreaRect.right - buttonSize - 20;
        float buttonY = ioAreaRect.top + 20;
        
        pauseButtonRect.set(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize);
        
        // Draw button with high contrast colors
        pauseButtonPaint.setColor(Color.parseColor("#FF0000")); // Pure red
        canvas.drawRoundRect(pauseButtonRect, 15, 15, pauseButtonPaint);
        
        // Add thick white border for better visibility
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6);
        canvas.drawRoundRect(pauseButtonRect, 15, 15, borderPaint);
        
        // Draw pause or play icon based on current state
        if (isPaused) {
            // Draw play triangle
            Path playIcon = new Path();
            float centerX = pauseButtonRect.centerX();
            float centerY = pauseButtonRect.centerY();
            float size = 30;
            
            playIcon.moveTo(centerX - size/2, centerY - size);
            playIcon.lineTo(centerX - size/2, centerY + size);
            playIcon.lineTo(centerX + size, centerY);
            playIcon.close();
            
            Paint iconPaint = new Paint();
            iconPaint.setColor(Color.WHITE);
            iconPaint.setStyle(Paint.Style.FILL);
            canvas.drawPath(playIcon, iconPaint);
        } else {
            // Draw pause bars
            float centerX = pauseButtonRect.centerX();
            float centerY = pauseButtonRect.centerY();
            float size = 25;
            
            Paint iconPaint = new Paint();
            iconPaint.setColor(Color.WHITE);
            iconPaint.setStyle(Paint.Style.FILL);
            
            // Left bar
            canvas.drawRect(centerX - size - 5, centerY - size, centerX - 5, centerY + size, iconPaint);
            // Right bar
            canvas.drawRect(centerX + 5, centerY - size, centerX + size + 5, centerY + size, iconPaint);
        }
        
        // Add text label below button for extra clarity
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        String buttonText = isPaused ? "PLAY" : "PAUSE";
        canvas.drawText(buttonText, pauseButtonRect.centerX(), pauseButtonRect.bottom + 30, textPaint);
        
        // Log for debugging
        Log.d("GameView", "Drawing pause button at " + pauseButtonRect.toString());
    }
    
    /**
     * Draws the pause overlay with resume button when the game is paused
     */
    private void drawPauseOverlay(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        
        // Full screen overlay
        canvas.drawRect(0, 0, width, height, pauseOverlayPaint);
        
        // Pause menu panel
        int panelWidth = width * 2/3;
        int panelHeight = height / 2; // Taller panel to fit two buttons
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = (height - panelHeight) / 2;
        RectF pausePanel = new RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight);
        canvas.drawRoundRect(pausePanel, 20, 20, pauseMenuPaint);
        
        // Pause title
        Paint titlePaint = new Paint(pauseButtonTextPaint);
        titlePaint.setTextSize(50);
        canvas.drawText("GAME PAUSED", pausePanel.centerX(), pausePanel.top + 80, titlePaint);
        
        // Button dimensions
        int buttonWidth = panelWidth / 2;
        int buttonHeight = 80;
        int buttonSpacing = 40; // Space between buttons
        
        // Resume button - positioned higher in the panel
        int resumeButtonTop = (int) pausePanel.top + 140;
        resumeButtonRect.set((width - buttonWidth) / 2, resumeButtonTop, 
                             (width + buttonWidth) / 2, resumeButtonTop + buttonHeight);
        
        // Draw resume button with green color
        Paint resumeButtonPaint = new Paint(pauseButtonPaint);
        resumeButtonPaint.setColor(Color.parseColor("#4CAF50")); // Green
        canvas.drawRoundRect(resumeButtonRect, 10, 10, resumeButtonPaint);
        canvas.drawText("RESUME", resumeButtonRect.centerX(), resumeButtonRect.centerY() + 15, pauseButtonTextPaint);
        
        // Quit button - positioned below the resume button
        int quitButtonTop = resumeButtonTop + buttonHeight + buttonSpacing;
        quitFromPauseRect.set((width - buttonWidth) / 2, quitButtonTop, 
                             (width + buttonWidth) / 2, quitButtonTop + buttonHeight);
        
        // Draw quit button with red color
        Paint quitButtonPaint = new Paint();
        quitButtonPaint.setColor(Color.parseColor("#D32F2F")); // Darker red
        quitButtonPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(quitFromPauseRect, 10, 10, quitButtonPaint);
        canvas.drawText("QUIT TO TITLE", quitFromPauseRect.centerX(), quitFromPauseRect.centerY() + 15, pauseButtonTextPaint);
        
        // Debug log to confirm overlay is drawing both buttons
        Log.d("GameView", "Drawing pause overlay with resume button at " + resumeButtonRect.toString() 
                + " and quit button at " + quitFromPauseRect.toString());
    }
    
    // Update the draw method to include pause button and overlay
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        
        if (canvas == null) {
            Log.e("GameView", "Canvas is null in draw method");
            return;
        }

        Log.d("GameView", "Drawing game view with canvas width: " + canvas.getWidth() + ", height: " + canvas.getHeight());

        // Fill background
        canvas.drawColor(backgroundPaint.getColor());
        
        // Draw game elements
        drawGame(canvas);
        
        // Draw system console at the bottom
        drawSystemConsole(canvas, getWidth(), getHeight());
        
        // IMPORTANT: Draw pause button above all other elements
        Log.d("GameView", "About to draw pause button");
        drawPauseButton(canvas);
        Log.d("GameView", "Finished drawing pause button");
        
        // Apply screen flash effect for health damage
        if (isScreenFlashing && screenFlashPaint.getAlpha() > 0) {
            // Draw full-screen semi-transparent red overlay
            canvas.drawRect(0, 0, getWidth(), getHeight(), screenFlashPaint);
        }
        
        // Draw floating damage indicator if active
        if (showDamageIndicator) {
            long elapsedTime = System.currentTimeMillis() - damageIndicatorStartTime;
            
            if (elapsedTime < DAMAGE_INDICATOR_DURATION_MS) {
                // Calculate fade out and float up effect
                float progress = (float) elapsedTime / DAMAGE_INDICATOR_DURATION_MS;
                int alpha = (int) (255 * (1.0f - progress)); // Fade out
                float offsetY = -100 * progress; // Float up 100 pixels
                
                // Apply animation effects
                damageTextPaint.setAlpha(alpha);
                
                // Draw the damage text
                canvas.drawText(damageText, damageTextX, damageTextY + offsetY, damageTextPaint);
                
                // Keep animation going
                invalidate();
            } else {
                // Animation complete
                showDamageIndicator = false;
            }
        }
        
        // Draw pause overlay if paused
        if (isPaused) {
            drawPauseOverlay(canvas);
        }
        
        // Draw game over UI if needed
        if (isGameOver) {
            drawGameOverOverlay(canvas);
        }
    }

    /**
     * Provides haptic feedback (vibration) when a user action cannot be completed due to lack of resources
     */
    private void vibrateForError() {
        Context context = getContext();
        if (context != null) {
            android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                // Check if we're on API 26 or higher for newer vibration API
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    // Create a sharp, error-like vibration effect
                    android.os.VibrationEffect errorVibration = android.os.VibrationEffect.createOneShot(
                        300, // 300ms duration
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    );
                    vibrator.vibrate(errorVibration);
                } else {
                    // Legacy vibration for older devices
                    vibrator.vibrate(300);
                }
                
                Log.d(TAG, "Vibration feedback provided for insufficient resources");
            }
        }
    }
    
    /**
     * Shows a temporary error message when there are insufficient resources
     */
    private void showInsufficientResourcesError() {
        // Set a flag to display an error message
        isShowingError = true;
        errorMessage = "Not enough memory!";
        errorDisplayStartTime = System.currentTimeMillis();
        
        // Create a handler to clear the error after a delay
        android.os.Handler handler = new android.os.Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isShowingError = false;
                invalidate(); // Trigger redraw
            }
        }, 1000); // Show error for 2 seconds
        
        // Force a redraw to show the error immediately
        invalidate();
    }

    /**
     * Draws an error message in the center of the screen
     */
    private void drawErrorMessage(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        
        // Calculate the center position
        float centerX = width / 2f;
        float centerY = height / 2f - 100; // Slightly above center
        
        // Create a semi-transparent background for the message
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(180); // Semi-transparent
        
        // Calculate the background dimensions
        float textWidth = errorMessagePaint.measureText(errorMessage);
        float padding = 30;
        RectF bgRect = new RectF(
            centerX - textWidth/2 - padding,
            centerY - 60,
            centerX + textWidth/2 + padding,
            centerY + 30
        );
        
        // Draw the background with rounded corners
        canvas.drawRoundRect(bgRect, 20, 20, bgPaint);
        
        // Draw the error message text
        canvas.drawText(errorMessage, centerX, centerY, errorMessagePaint);
    }

    /**
     * Draws the client area with improved terminal-style visualization
     */
    private void drawClientArea(Canvas canvas) {
        // Draw client area border
        Paint borderPaint = new Paint(clientAreaPaint);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4);
        
        // Draw tech-inspired background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#B71C1C")); // Dark red
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(clientAreaRect, bgPaint);
        
        // Draw circuit pattern in the background
        Paint circuitPaint = new Paint();
        circuitPaint.setColor(Color.parseColor("#EF9A9A")); // Light red
        circuitPaint.setStrokeWidth(1);
        circuitPaint.setStyle(Paint.Style.STROKE);
        circuitPaint.setAlpha(40); // Very subtle
        
        // Draw pattern lines
        for (int i = 1; i < 10; i++) {
            float x = clientAreaRect.left + (clientAreaRect.width() * i / 10f);
            float y = clientAreaRect.top + (clientAreaRect.height() * i / 10f);
            canvas.drawLine(clientAreaRect.left, y, clientAreaRect.right, y, circuitPaint);
            canvas.drawLine(x, clientAreaRect.top, x, clientAreaRect.bottom, circuitPaint);
        }
        
        // Draw title
        Paint titlePaint = new Paint(textPaint);
        titlePaint.setTextSize(28);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTypeface(Typeface.MONOSPACE);
        String title = "> Clients";
        canvas.drawText(title, clientAreaRect.left + 20, clientAreaRect.top + 35, titlePaint);
        
        // Get client status from game manager
        List<Client> clients = gameManager.getClients();
        
        // Define layout parameters for client terminals
        int numClients = clients.size();
        float terminalWidth = Math.min(180, clientAreaRect.width() / (numClients + 1));
        float terminalHeight = 120;
        float terminalSpacing = (clientAreaRect.width() - (terminalWidth * numClients)) / (numClients + 1);
        float terminalY = clientAreaRect.top + 60;
        
        // Draw each client as a terminal window
        for (int i = 0; i < numClients; i++) {
            Client client = clients.get(i);
            boolean isBusy = client.isConsuming();
            
            // Calculate terminal position
            float terminalX = clientAreaRect.left + terminalSpacing + i * (terminalWidth + terminalSpacing);
            
            // Draw terminal window
            RectF terminalRect = new RectF(
                terminalX, 
                terminalY, 
                terminalX + terminalWidth, 
                terminalY + terminalHeight
            );
            
            // Draw terminal background
            Paint terminalBgPaint = new Paint();
            terminalBgPaint.setColor(Color.parseColor("#212121")); // Dark gray
            canvas.drawRoundRect(terminalRect, 8, 8, terminalBgPaint);
            
            // Draw terminal title bar
            RectF titleBarRect = new RectF(
                terminalRect.left,
                terminalRect.top,
                terminalRect.right,
                terminalRect.top + 20
            );
            Paint titleBarPaint = new Paint();
            titleBarPaint.setColor(isBusy ? 
                Color.parseColor("#E53935") : // Red for busy
                Color.parseColor("#43A047")); // Green for idle
            canvas.drawRoundRect(new RectF(titleBarRect.left, titleBarRect.top, titleBarRect.right, titleBarRect.top + 8), 
                                8, 8, titleBarPaint);
            canvas.drawRect(new RectF(titleBarRect.left, titleBarRect.top + 8, titleBarRect.right, titleBarRect.bottom), 
                           titleBarPaint);
            
            // Draw window control buttons in title bar
            float buttonRadius = 3;
            float buttonY = titleBarRect.top + 10;
            float buttonSpacing = 10;
            
            // Close button (red)
            Paint closeButtonPaint = new Paint();
            closeButtonPaint.setColor(Color.parseColor("#FF5252"));
            canvas.drawCircle(titleBarRect.left + 10, buttonY, buttonRadius, closeButtonPaint);
            
            // Minimize button (yellow)
            Paint minButtonPaint = new Paint();
            minButtonPaint.setColor(Color.parseColor("#FFEB3B"));
            canvas.drawCircle(titleBarRect.left + 10 + buttonSpacing, buttonY, buttonRadius, minButtonPaint);
            
            // Maximize button (green)
            Paint maxButtonPaint = new Paint();
            maxButtonPaint.setColor(Color.parseColor("#4CAF50"));
            canvas.drawCircle(titleBarRect.left + 10 + 2 * buttonSpacing, buttonY, buttonRadius, maxButtonPaint);
            
            // Draw client title
            Paint clientTitlePaint = new Paint();
            clientTitlePaint.setColor(Color.WHITE);
            clientTitlePaint.setTextSize(14);
            clientTitlePaint.setTextAlign(Paint.Align.CENTER);
            clientTitlePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            canvas.drawText("Client " + i, terminalRect.centerX(), titleBarRect.bottom - 5, clientTitlePaint);
            
            // Draw terminal content
            RectF contentRect = new RectF(
                terminalRect.left + 5,
                titleBarRect.bottom + 5,
                terminalRect.right - 5,
                terminalRect.bottom - 5
            );
            
            // Draw terminal screen (darker background)
            Paint screenPaint = new Paint();
            screenPaint.setColor(Color.parseColor("#1A1A1A")); // Very dark gray
            canvas.drawRect(contentRect, screenPaint);
            
            // Draw terminal content based on client state
            if (isBusy) {
                // Busy processing - show activity graph
                drawClientActivityGraph(canvas, contentRect, client);
            } else {
                // Idle - show blinking cursor
                Paint cursorPaint = new Paint();
                cursorPaint.setColor(Color.parseColor("#FFFFFF")); // White
                cursorPaint.setTextSize(16);
                cursorPaint.setTypeface(Typeface.MONOSPACE);
                
                // Only show cursor at fixed intervals for stability
                long timestamp = System.currentTimeMillis();
                boolean showCursor = (timestamp / 500) % 2 == 0;
                
                canvas.drawText("$>", contentRect.left + 5, contentRect.top + 20, cursorPaint);
                if (showCursor) {
                    canvas.drawText("_", contentRect.left + 25, contentRect.top + 20, cursorPaint);
                }
                
                // Draw "Ready" text
                Paint readyPaint = new Paint(cursorPaint);
                readyPaint.setTextSize(14);
                readyPaint.setColor(Color.parseColor("#4CAF50")); // Green
                canvas.drawText("[READY]", contentRect.left + 5, contentRect.bottom - 10, readyPaint);
            }
            
            // Draw client status label below the terminal
            Paint statusPaint = new Paint();
            statusPaint.setTextSize(16);
            statusPaint.setTextAlign(Paint.Align.CENTER);
            statusPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            
            String statusText = isBusy ? "PROCESSING" : "IDLE";
            statusPaint.setColor(isBusy ? Color.parseColor("#FF8A80") : Color.parseColor("#B9F6CA"));
            canvas.drawText(statusText, terminalRect.centerX(), terminalRect.bottom + 20, statusPaint);
        }
    }

     /**
     * Draws an activity graph in the client terminal when busy
     */
    private void drawClientActivityGraph(Canvas canvas, RectF rect, Client client) {
        // Draw grid lines
        Paint gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#424242")); // Dark gray
        gridPaint.setStrokeWidth(1);
        
        // Horizontal grid lines
        for (int i = 1; i < 4; i++) {
            float y = rect.top + (rect.height() * i / 4);
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint);
        }
        
        // Vertical grid lines
        for (int i = 1; i < 6; i++) {
            float x = rect.left + (rect.width() * i / 6);
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint);
        }
        
        // Draw CPU usage graph
        Paint graphPaint = new Paint();
        graphPaint.setColor(Color.parseColor("#FF5252")); // Red
        graphPaint.setStrokeWidth(2);
        graphPaint.setStyle(Paint.Style.STROKE);
        
        // Get client process for remaining time
        Process p = client.getCurrentProcess();
        if (p != null) {
            // Calculate a simple estimate for consumption progress
            // This is a replacement for the missing getConsumptionProgressRatio method
            float progressRatio = 0.5f; // Default to 50% if we can't calculate
            
            // Draw activity line
            Path graphPath = new Path();
            float startX = rect.left + 5;
            float endX = rect.right - 5;
            float height = rect.height() - 10;
            
            graphPath.moveTo(startX, rect.bottom - 5);
            
            // Add some randomness to the graph to make it look like activity
            for (int i = 0; i < 20; i++) {
                float x = startX + (endX - startX) * i / 19f;
                // Combine progress with random variation
                float randomVariation = (float) (Math.sin(i * 0.5) * 0.1) + (float)(Math.random() * 0.05);
                float ratio = Math.max(0.1f, Math.min(0.9f, progressRatio + randomVariation));
                float y = rect.bottom - 5 - (height * ratio);
                graphPath.lineTo(x, y);
            }
            
            canvas.drawPath(graphPath, graphPaint);
            
            // Draw process info
            Paint infoPaint = new Paint();
            infoPaint.setColor(Color.parseColor("#FFFFFF"));
            infoPaint.setTextSize(12);
            infoPaint.setTypeface(Typeface.MONOSPACE);
            
            // Show process ID being consumed
            canvas.drawText("PROC: P" + p.getId(), rect.left + 5, rect.top + 15, infoPaint);
            
            // Show consumption progress percentage - use fixed value
            int percent = 50; // Default 50%
            canvas.drawText("PROG: " + percent + "%", rect.left + 5, rect.top + 30, infoPaint);
        } else {
            // Should not happen, but just in case
            Paint errorPaint = new Paint();
            errorPaint.setColor(Color.RED);
            errorPaint.setTextSize(14);
            canvas.drawText("ERROR", rect.centerX() - 20, rect.centerY(), errorPaint);
        }
    }

    /**
     * Highlights valid drop zones based on the type of process being dragged.
     * Different zones are highlighted depending on the current state of the process.
     */
    private void drawDropZoneHighlights(Canvas canvas, Process process) {
        if (process == null) return;
        
        Process.ProcessState state = process.getCurrentState();
        
        // Different highlighting logic based on process state
        switch (state) {
            case IN_QUEUE:
                // When dragging from queue, highlight all cores
             for (Map.Entry<Integer, Rect> entry : coreAreaRects.entrySet()) {
                 Core core = gameManager.getCpuCores().get(entry.getKey());
                 if (!core.isUtilized()) {
                        // Only highlight empty cores
                              canvas.drawRect(entry.getValue(), dropZoneHighlightPaint);
                          }
                }
                break;
                
            case ON_CORE:
                // For IO processes that are waiting for IO, highlight the IO area
                if (process instanceof IOProcess) {
                    IOProcess ioProcess = (IOProcess) process;
                    if (ioProcess.isCpuPausedForIO()) {
                        canvas.drawRect(ioAreaRect, dropZoneHighlightPaint);
                    }
                }
                break;
                
            case IN_IO:
                // For completed IO processes, highlight all cores
                if (process instanceof IOProcess) {
                    IOProcess ioProcess = (IOProcess) process;
                    if (ioProcess.isIoCompleted()) {
                        for (Map.Entry<Integer, Rect> entry : coreAreaRects.entrySet()) {
                            Core core = gameManager.getCpuCores().get(entry.getKey());
                            if (!core.isUtilized()) {
                                // Only highlight empty cores
                          canvas.drawRect(entry.getValue(), dropZoneHighlightPaint);
                      }
                 }
                    }
                }
                break;
        }
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
                    RectF pBounds = getProcessVisualBoundsOnCore(core.getId(), p);
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
        if (gameManager.getIoArea().getCurrentProcess() != null && 
            gameManager.getIoArea().getCurrentProcess().getId() == process.getId()) {
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
            if(core.isUtilized() && core.getCurrentProcess() != null && 
               core.getCurrentProcess().getId() == processId) {
                return core.getId();
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
                        // Check if there's enough memory to allocate the process
                        if (!gameManager.getMemory().hasEnoughMemory(droppedProcess.getMemoryRequirement())) {
                            // Not enough memory - provide vibration feedback
                            vibrateForError();
                            showInsufficientResourcesError();
                        } else {
                            gameManager.moveProcessFromQueueToCore(droppedProcess.getId(), coreId);
                        }
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
                    int sourceCoreId = (originalSourceCoreId != -1) ? originalSourceCoreId : 
                                      findCoreIdForProcess(droppedProcess.getId());
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
} 