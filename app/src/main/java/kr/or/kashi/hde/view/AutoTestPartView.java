/*
 * Copyright (C) 2023 Korea Association of AI Smart Home.
 * Copyright (C) 2023 KyungDong Navien Co, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kr.or.kashi.hde.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import junit.framework.TestCase;
import junit.framework.TestResult;

import java.util.List;

import kr.or.kashi.hde.HomeDevice;
import kr.or.kashi.hde.R;
import kr.or.kashi.hde.test.HomeDeviceTestCallback;
import kr.or.kashi.hde.test.HomeDeviceTestRunner;
import kr.or.kashi.hde.util.DebugLog;

public class AutoTestPartView extends LinearLayout implements HomeDeviceTestCallback {
    private static final String TAG = AutoTestPartView.class.getSimpleName();
    private final Context mContext;
    private final Handler mHandler;
    private final HomeDeviceTestRunner mDeviceTestRunner;
    private final AutoTestReportDialog mReportDialog;

    private TextView mTestStateText;
    private TextView mTestProgressText;
    private Button mReportButton;
    private AutoTestResultView mTestResultView;
    private ProgressBar mTestProgress;

    public AutoTestPartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mDeviceTestRunner = new HomeDeviceTestRunner(mHandler);
        mReportDialog = new AutoTestReportDialog(context);
    }

    public void init() {
        mTestStateText = findViewById(R.id.test_state_text);
        mTestProgressText = findViewById(R.id.test_progress_text);

        mReportButton = findViewById(R.id.report_button);
        mReportButton.setOnClickListener(view -> onReportClicked());

        mTestResultView = findViewById(R.id.test_result_view);

        mTestProgress = findViewById(R.id.test_progress);

        mDeviceTestRunner.addCallback(this);
        mDeviceTestRunner.addCallback(mTestResultView);
        mDeviceTestRunner.addCallback(mReportDialog);
    }

    public HomeDeviceTestRunner getTestRunner() {
        return mDeviceTestRunner;
    }

    public boolean startTest(List<HomeDevice> devices) {
        return mDeviceTestRunner.start(devices);
    }

    public void stopTest() {
        mDeviceTestRunner.stop();
    }

    private void onReportClicked() {
        mReportDialog.show();
    }

    @Override
    public void onTestRunnerStarted() {
        DebugLog.printEvent("onTestRunnerStarted");
        mTestProgress.setVisibility(View.VISIBLE);
        mReportButton.setEnabled(false);
        mReportButton.setTextColor(0xFF888888);
        mTestResultView.clear();
        mTestStateText.setText("RUNNING");
        mTestProgressText.setText("0%");
    }

    @Override
    public void onTestRunnerFinished() {
        DebugLog.printEvent("onTestRunnerFinished");
        mReportButton.setEnabled(true);
        mReportButton.setTextColor(0xFF000000);
        mTestStateText.setText("STOPPED");
        mTestProgressText.setText("100%");
        mTestProgress.setVisibility(View.GONE);
    }

    @Override
    public void onDeviceTestStarted(HomeDevice device) {
        DebugLog.printEvent("onDeviceTestStarted device:" + device.getAddress());
    }

    @Override
    public void onDeviceTestExecuted(HomeDevice device, TestCase test, TestResult result, int progress) {
        DebugLog.printEvent("test... device:" + device.getAddress() + " case:" + test.getName() + " pass:" + result.wasSuccessful());
        mTestProgressText.setText(progress + "%");
    }

    @Override
    public void onDeviceTestFinished(HomeDevice device) {
        DebugLog.printEvent("onDeviceTestFinished device:" + device.getAddress());
    }
}
