package com.madrapps.pikolo.components

import android.graphics.Canvas
import android.graphics.Color.BLACK
import android.graphics.Color.WHITE
import android.graphics.Color.blue
import android.graphics.Color.green
import android.graphics.Color.red
import android.graphics.Matrix
import android.graphics.Paint.Cap.ROUND
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.ColorUtils
import com.madrapps.pikolo.Metrics
import com.madrapps.pikolo.Paints

internal abstract class ArcComponent(
        metrics: Metrics,
        paints: Paints,
        arcLength: Float,
        arcStartAngle: Float
) : ColorComponent(metrics, paints, arcLength, arcStartAngle) {

    protected abstract val componentIndex: Int
    abstract val noOfColors: Int
    internal abstract val colors: IntArray
    internal abstract val colorPosition: FloatArray

    private val matrix = Matrix()
    private lateinit var shader: Shader
    private var innerCircleArcReference: RectF? = null

    /**
     * This is the max value of the component. For now the min value is taken as 0
     */
    abstract val range: Float

    private val arcEndAngle: Float
        get() {
            val end = arcStartAngle + arcLength
            return if (end > 360f) end - 360f else end
        }

    init {
        angle = (arcStartAngle + arcLength / 2f).toDouble()
    }

    override fun drawComponent(canvas: Canvas) {
        drawArc(canvas)
        drawIndicator(canvas)
    }

    internal open fun drawArc(canvas: Canvas) {
        val shaderPaint = paints.shaderPaint
        shaderPaint.style = STROKE
        shaderPaint.strokeCap = ROUND

        if (innerCircleArcReference == null) {
            innerCircleArcReference = RectF(metrics.centerX - radius, metrics.centerY - radius, metrics.centerX + radius, metrics.centerY + radius)
        }
        innerCircleArcReference?.let {
            if (strokeWidth > 0) {
                shaderPaint.shader = null
                shaderPaint.color = if (strokeColor == 0) WHITE else strokeColor
                shaderPaint.strokeWidth = fillWidth + strokeWidth * 2
                canvas.drawArc(it, arcStartAngle, arcLength, false, shaderPaint)
            }

            shaderPaint.strokeWidth = fillWidth
            shaderPaint.shader = getShader()
            canvas.drawArc(it, arcStartAngle, arcLength, false, shaderPaint)
        }
    }

    internal open fun drawIndicator(canvas: Canvas) {
        indicatorX = (metrics.centerX + radius * Math.cos(Math.toRadians(angle))).toFloat()
        indicatorY = (metrics.centerY + radius * Math.sin(Math.toRadians(angle))).toFloat()

        val indicatorPaint = paints.indicatorPaint
        indicatorPaint.style = FILL

        val color = metrics.getColor()
        indicatorPaint.color = color

        var drawStroke = indicatorStrokeWidth > 0
        if (drawStroke && indicatorStrokeShadow) {
            drawStroke = false
            indicatorPaint.isAntiAlias = true
            indicatorPaint.setShadowLayer(indicatorStrokeWidth * 2f,
                    0f, 0f, getBorderColor(color))
        }
        canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, indicatorPaint)

        if (drawStroke) {
            indicatorPaint.color = getBorderColor(color)
            indicatorPaint.style = STROKE
            indicatorPaint.strokeWidth = indicatorStrokeWidth
            canvas.drawCircle(indicatorX, indicatorY, indicatorRadius, indicatorPaint)
        }
    }

    private fun getColorDarkness(color: Int): Double {
        // The formula is from https://en.wikipedia.org/wiki/Luma_%28video%29
        return 1 - (0.299 * red(color) + 0.587 * green(color) + 0.114 * blue(color)) / 255
    }

    private fun getBorderColor(color: Int): Int {
        if (indicatorStrokeColor != 0) {
            return indicatorStrokeColor
        }
        val colorDarkness = getColorDarkness(color).toFloat()
        return if (colorDarkness >= 0.5) {
            ColorUtils.blendARGB(color, WHITE, colorDarkness)
        } else {
            ColorUtils.blendARGB(color, BLACK, 0.75f - colorDarkness)
        }
    }

    override fun getShader(): Shader {
        with(metrics) {
            getColorArray(color.copyOf())
            getColorPositionArray()
            shader = SweepGradient(centerX, centerY, colors, colorPosition)
            // We need a margin of rotation due to the Paint.Cap.Round
            matrix.setRotate(arcStartAngle - (fillWidth / 3f / density), centerX, centerY)
            shader.setLocalMatrix(matrix)
        }

        return shader
    }

    internal abstract fun getColorArray(color: FloatArray): IntArray

    private fun getColorPositionArray(): FloatArray {
        for (i in 0 until noOfColors) {
            colorPosition[i] = i * (arcLength / (noOfColors - 1)) / 360f
        }
        return colorPosition
    }

    override fun calculateAngle(x1: Float, y1: Float) {
        super.calculateAngle(x1, y1)
        // Don't let the indicator go outside the arc
        // limit the indicator between arcStartAngle and arcEndAngle
        val associatedArcLength = 360f - arcLength
        val middleOfAssociatedArc = arcEndAngle + associatedArcLength / 2f
        if (arcEndAngle < arcStartAngle) {
            calculateAngleInContinuousRange(middleOfAssociatedArc)
        } else if (arcEndAngle > arcStartAngle) {
            calculateAngleInNonContinuousRange(middleOfAssociatedArc)
        }
    }

    /**
     * This would be the case when [arcStartAngle]=285 and [arcEndAngle]=75, so that the arc has the 0 degree crossover. This means that the
     * associated arc (360 - [arcLength]) is a continuous range. When the angle is in this range, we need to either set it to the [arcStartAngle]
     * or the [arcEndAngle]
     *
     * @param middle the middle point (in angle) of the associated arc
     */
    private fun calculateAngleInContinuousRange(middle: Float) {
        when (angle) {
            in arcEndAngle..middle -> angle = arcEndAngle.toDouble()
            in middle..arcStartAngle -> angle = arcStartAngle.toDouble()
        }
    }

    /**
     * This is the case where the arc is a continuous range, i.e, the 0 crossover occurs in the associated arc. This can happen in two ways.
     *
     * 1. The [middle] point can be before the 0 degree. Eg. [arcStartAngle]=10 and [arcEndAngle]=120
     * 2. The [middle] point can be after the 0 degree. Eg. [arcStartAngle]=100 and [arcEndAngle]=350
     *
     * @param middle the middle point (in angle) of the associated arc
     */
    private fun calculateAngleInNonContinuousRange(middle: Float) {
        if (middle > 360f) {
            val correctedMiddle = middle - 360f
            when (angle) {
                in arcEndAngle..360f, in 0f..correctedMiddle -> angle = arcEndAngle.toDouble()
                in correctedMiddle..arcStartAngle -> angle = arcStartAngle.toDouble()
            }
        } else {
            when (angle) {
                in arcEndAngle..middle -> angle = arcEndAngle.toDouble()
                in middle..360f, in 0f..arcStartAngle -> angle = arcStartAngle.toDouble()
            }
        }
    }

    override fun updateComponent(angle: Double) {
        var relativeAngle = angle
        if (angle < arcStartAngle) {
            relativeAngle += 360f
        }

        val baseAngle = relativeAngle - arcStartAngle
        val component = (baseAngle / arcLength) * range

        metrics.color[componentIndex] = component.toFloat()
    }

    override fun updateAngle(component: Float) {
        val baseAngle = component / range * arcLength
        val relativeAngle = baseAngle + arcStartAngle

        angle = relativeAngle.toDouble()
    }

}