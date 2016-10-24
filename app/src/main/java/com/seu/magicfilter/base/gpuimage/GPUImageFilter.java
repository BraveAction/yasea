/*
 * Copyright (C) 2012 CyberAgent
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.seu.magicfilter.base.gpuimage;

import android.graphics.PointF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.seu.magicfilter.utils.MagicFilterType;
import com.seu.magicfilter.utils.OpenGlUtils;
import com.seu.magicfilter.utils.Rotation;
import com.seu.magicfilter.utils.TextureRotationUtil;
import com.ximalaya.android.streaming.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedList;

public class GPUImageFilter {

    private MagicFilterType mType = MagicFilterType.NONE;
    private final LinkedList<Runnable> mRunOnDraw;
    private final String mVertexShader;
    private final String mFragmentShader;
    private boolean mIsInitialized;

    private int mGLProgId;
    private int mGLAttribPosition;
    private int mGLUniformTexture;
    private int mGLAttribTextureCoordinate;
    private int mGLTextureTransformMatrixLocation;

    protected int mInputWidth;
    protected int mInputHeight;
    protected int mOutputWidth;
    protected int mOutputHeight;
    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mGLTextureBuffer;
    private int[] mGLCubeBuffers;
    private int[] mGLTextureBuffers;
    private float[] mGLTextureTransformMatrix;

    private IntBuffer mGLFboBuffer;
    private int[] mGLFrameBuffers;
    private int[] mGLFrameBufferTextures;

    public GPUImageFilter() {
        this(MagicFilterType.NONE);
    }

    public GPUImageFilter(MagicFilterType type) {
        this(type, OpenGlUtils.readShaderFromRawResource(R.raw.default_vertex),
            OpenGlUtils.readShaderFromRawResource(R.raw.default_fragment));
    }

    public GPUImageFilter(MagicFilterType type, String fragmentShader) {
        this(type, OpenGlUtils.readShaderFromRawResource(R.raw.default_vertex), fragmentShader);
    }

    public GPUImageFilter(MagicFilterType type, String vertexShader, String fragmentShader) {
        mType = type;
        mRunOnDraw = new LinkedList<>();
        mVertexShader = vertexShader;
        mFragmentShader = fragmentShader;
        
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(TextureRotationUtil.getRotation(Rotation.NORMAL, false, true)).position(0);
    }

    public void init() {
        onInit();
        onInitialized();
    }

    protected void onInit() {
        initVbo();
        mGLProgId = OpenGlUtils.loadProgram(mVertexShader, mFragmentShader);
        mGLAttribPosition = GLES20.glGetAttribLocation(mGLProgId, "position");
        mGLAttribTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId,"inputTextureCoordinate");
        mGLTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
        mGLUniformTexture = GLES20.glGetUniformLocation(mGLProgId, "inputImageTexture");
    }

    protected void onInitialized() {
        mIsInitialized = true;
    }

    public final void destroy() {
        mIsInitialized = false;
        destroyFboTexture();
        destoryVbo();
        GLES20.glDeleteProgram(mGLProgId);
        onDestroy();
    }

    protected void onDestroy() {
    }

    public void onInputSizeChanged(final int width, final int height) {
        mInputWidth = width;
        mInputHeight = height;
        initFboTexture(width, height);
    }

    public void onDisplaySizeChanged(final int width, final int height) {
        mOutputWidth = width;
        mOutputHeight = height;
    }

    private void initVbo() {
        mGLCubeBuffers = new int[1];
        mGLTextureBuffers = new int[1];

        GLES20.glGenBuffers(1, mGLCubeBuffers, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffer.capacity() * 4, mGLCubeBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glGenBuffers(1, mGLTextureBuffers, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffer.capacity() * 4, mGLTextureBuffer, GLES20.GL_STATIC_DRAW);
    }

    private void destoryVbo() {
        if (mGLCubeBuffers != null) {
            GLES20.glDeleteBuffers(1, mGLCubeBuffers, 0);
            mGLCubeBuffers = null;
        }
        if (mGLTextureBuffers != null) {
            GLES20.glDeleteBuffers(1, mGLTextureBuffers, 0);
            mGLTextureBuffers = null;
        }
    }

    private void initFboTexture(int width, int height) {
        if (mGLFrameBuffers != null && (mInputWidth != width || mInputHeight != height)) {
            destroyFboTexture();
        }

        mGLFrameBuffers = new int[1];
        mGLFrameBufferTextures = new int[1];
        mGLFboBuffer = IntBuffer.allocate(width * height);

        GLES20.glGenFramebuffers(1, mGLFrameBuffers, 0);
        GLES20.glGenTextures(1, mGLFrameBufferTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mGLFrameBufferTextures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mGLFrameBuffers[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mGLFrameBufferTextures[0], 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void destroyFboTexture() {
        if (mGLFrameBufferTextures != null) {
            GLES20.glDeleteTextures(1, mGLFrameBufferTextures, 0);
            mGLFrameBufferTextures = null;
        }
        if (mGLFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mGLFrameBuffers, 0);
            mGLFrameBuffers = null;
        }
    }

    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer, final FloatBuffer textureBuffer) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }

        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        onDrawArraysAfter();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);

        return OpenGlUtils.ON_DRAWN;
    }

    public int onDrawFrame(int textureId) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }

        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffers[0]);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffers[0]);

        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glUniformMatrix4fv(mGLTextureTransformMatrixLocation, 1, false, mGLTextureTransformMatrix, 0);

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        onDrawArraysAfter();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return OpenGlUtils.ON_DRAWN;
    }

    public int onDrawFrameOES(int textureId) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }

        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffers[0]);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffers[0]);

        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glUniformMatrix4fv(mGLTextureTransformMatrixLocation, 1, false, mGLTextureTransformMatrix, 0);

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        onDrawArraysAfter();

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return OpenGlUtils.ON_DRAWN;
    }

    public int onDrawToTexture(int textureId) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }
        if (mGLFrameBuffers == null) {
            return OpenGlUtils.NO_TEXTURE;
        }

        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffers[0]);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffers[0]);

        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, 0);
        GLES20.glUniformMatrix4fv(mGLTextureTransformMatrixLocation, 1, false, mGLTextureTransformMatrix, 0);

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        onDrawArraysPre();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mGLFrameBuffers[0]);
        GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mGLFboBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        onDrawArraysAfter();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return mGLFrameBufferTextures[0];
    }

    public int onDrawToTextureOES(int textureId) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }
        if (mGLFrameBuffers == null) {
            return OpenGlUtils.NO_TEXTURE;
        }

        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();

//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLCubeBuffers[0]);
//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mGLTextureBuffers[0]);

        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 4 * 2, mGLCubeBuffer);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 4 * 2, mGLTextureBuffer);
        GLES20.glUniformMatrix4fv(mGLTextureTransformMatrixLocation, 1, false, mGLTextureTransformMatrix, 0);

        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        onDrawArraysPre();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mGLFrameBuffers[0]);
        GLES20.glViewport(0, 0, mInputWidth, mInputHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
        GLES20.glReadPixels(0, 0, mInputWidth, mInputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mGLFboBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        onDrawArraysAfter();

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);

//        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        return mGLFrameBufferTextures[0];
    }

    protected void onDrawArraysPre() {}
    protected void onDrawArraysAfter() {}
    
    protected void runPendingOnDrawTasks() {
        while (!mRunOnDraw.isEmpty()) {
            mRunOnDraw.removeFirst().run();
        }
    }

    public MagicFilterType getFilterType() {
        return mType;
    }

    public int getIntputWidth() {
        return mInputWidth;
    }

    public int getIntputHeight() {
        return mInputHeight;
    }

    public int getProgram() {
        return mGLProgId;
    }

    public int getAttribPosition() {
        return mGLAttribPosition;
    }

    public int getAttribTextureCoordinate() {
        return mGLAttribTextureCoordinate;
    }

    public int getUniformTexture() {
        return mGLUniformTexture;
    }

    public IntBuffer getGlFboBuffer() {
        return mGLFboBuffer;
    }

    public void setTextureTransformMatrix(float[] mtx){
        mGLTextureTransformMatrix = mtx;
    }

    protected void setInteger(final int location, final int intValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1i(location, intValue);
            }
        });
    }

    protected void setFloat(final int location, final float floatValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1f(location, floatValue);
            }
        });
    }

    protected void setFloatVec2(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec3(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec4(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatArray(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1fv(location, arrayValue.length, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setPoint(final int location, final PointF point) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                float[] vec2 = new float[2];
                vec2[0] = point.x;
                vec2[1] = point.y;
                GLES20.glUniform2fv(location, 1, vec2, 0);
            }
        });
    }

    protected void setUniformMatrix3f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix3fv(location, 1, false, matrix, 0);
            }
        });
    }

    protected void setUniformMatrix4f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0);
            }
        });
    }

    protected void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.addLast(runnable);
        }
    }
}
