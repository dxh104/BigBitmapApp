package com.example.bigbitmapapp.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;

import com.example.bigbitmapapp.R;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by XHD on 2020/12/1
 * 区域展示的imageview
 */
@SuppressLint("AppCompatCustomView")
public class RegionImageView extends ImageView {
    private BitmapRegionDecoder mRegionDecoder;//区域解码器
    private BitmapFactory.Options mOptions = new BitmapFactory.Options();
    private int mImageWidth;//原图宽
    private int mImageHeight;//原图高
    private Bitmap mBitmap;
    private InputStream mInputStream;
    private Matrix mMatrix = new Matrix();
    private int image_resId;//属性 图片resId
    private float scaleBias = 1;//缩放会导致bitmap变大不建议缩放
    private int rate = 5;//滑动速率

    public RegionImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttrs(attrs);
        init();
    }

    private void initAttrs(AttributeSet attrs) {
        TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.RegionImageView);
        image_resId = typedArray.getResourceId(R.styleable.RegionImageView_image_resId, 0);
        typedArray.recycle();
    }

    private void init() {
        mInputStream = getResources().openRawResource(image_resId);
        setRegionDecoder(mInputStream);
    }


    private void updateRegion(int x, int y, boolean isInvalidate) {
        x = rate * x;
        y = rate * y;
        mRectLeft = x;
        mRectTop = y;
        mRectRight = (int) (mRectLeft + measuredWidth * scaleBias);
        mRectBottom = (int) (mRectTop + measuredHeight * scaleBias);
        if (mRectLeft < 0) {
            mRectLeft = 0;
            mRectRight = (int) (measuredWidth * scaleBias);
            dx = mRectLeft / rate;
        }
        if (mRectTop < 0) {
            mRectTop = 0;
            mRectBottom = (int) (measuredHeight * scaleBias);
            dy = mRectTop / rate;
        }
        if (mRectLeft > mImageWidth - measuredWidth * scaleBias) {
            mRectLeft = (int) (mImageWidth - measuredWidth * scaleBias);
            mRectRight = mImageWidth;
            dx = mRectLeft / rate;
        }
        if (mRectTop > mImageHeight - measuredHeight * scaleBias) {
            mRectTop = (int) (mImageHeight - measuredHeight * scaleBias);
            mRectBottom = mImageHeight;
            dy = mRectTop / rate;
        }
        if (isInvalidate)
            invalidate();
    }

    //设置区域解码器
    public void setRegionDecoder(InputStream inputStream) {
        mOptions.inJustDecodeBounds = true;//设置此参数是仅仅读取图片的宽高到options中，不会将整张图片读到内存中，防止oom
        BitmapFactory.decodeStream(inputStream, null, mOptions);
        mImageWidth = mOptions.outWidth;
        mImageHeight = mOptions.outHeight;
        mOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        mOptions.inJustDecodeBounds = false;
        try {
            //区域解码器
            mRegionDecoder = BitmapRegionDecoder.newInstance(inputStream, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int mRectLeft, mRectTop, mRectRight, mRectBottom;
    private Rect mRect = new Rect();
    private int measuredWidth;
    private int measuredHeight;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measuredWidth = getMeasuredWidth();
        measuredHeight = getMeasuredHeight();
        updateRegion(0, 0, false);//默认左上角
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        scale(canvas);//pass不用缩放压缩的方式
//        super.onDraw(canvas);//直接绘制大图java.lang.RuntimeException: Canvas: trying to draw too large(275646000bytes) bitmap. 意思262mb太大了

        //如果设置，则采用Options对象的解码方法将在加载内容时尝试重用此位图。
        mOptions.inBitmap = mBitmap;
        mRect.set(mRectLeft, mRectTop, mRectRight, mRectBottom);
        mMatrix.setScale(1 / scaleBias, 1 / scaleBias);
        mBitmap = mRegionDecoder.decodeRegion(mRect, mOptions);
        canvas.drawBitmap(mBitmap, mMatrix, null);

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            if (mInputStream != null) {
                mInputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private float dx, dy, downX, downY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX() + dx;
                downY = event.getRawY() + dy;
                break;
            case MotionEvent.ACTION_MOVE:
                dx = downX - event.getRawX();
                dy = downY - event.getRawY();
                updateRegion((int) dx, (int) dy, true);
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }


    private Matrix matrix = new Matrix();
    private Bitmap bitmap;
    private int drawCount = 0;
    Bitmap newBitmap;

    //缩放相当于压缩原bitmap体积
    private void scale(Canvas canvas) {
        if (drawCount == 0) {
            bitmap = ((BitmapDrawable) getDrawable()).getBitmap();//获取bitmap
            //新bitmap尺寸原来1/10 相当于原bitmap大小的1/100  2.62mb
            newBitmap = Bitmap.createBitmap(bitmap.getWidth() / 10, bitmap.getHeight() / 10, bitmap.getConfig());
            Canvas newCanvas = new Canvas(newBitmap);
            matrix.setScale(0.1f, 0.1f);//矩阵原图缩放1/10
            newCanvas.drawBitmap(bitmap, matrix, null);
            setImageBitmap(null);//置空释放原bitmap引用消耗的内存
            bitmap = null;//置空释放原bitmap引用消耗的内存
            drawCount++;
        }
        matrix.setScale(1f, 1f);
        canvas.drawBitmap(newBitmap, matrix, null);
    }


}
