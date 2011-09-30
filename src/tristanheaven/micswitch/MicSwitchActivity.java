/*
 *  Copyright (C) 2011 Tristan Heaven <tristanheaven@gmail.com>
 *
 *  This software is provided 'as-is', without any express or implied
 *  warranty.  In no event will the author(s) be held liable for any damages
 *  arising from the use of this software.
 *
 *  Permission is granted to anyone to use this software for any purpose,
 *  including commercial applications, and to alter it and redistribute it
 *  freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software
 *     in a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *  3. This notice may not be removed or altered from any source distribution.
 *
 */

package tristanheaven.micswitch;

import java.io.DataOutputStream;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

public class MicSwitchActivity extends Activity {
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		if (!programCheck()) {
			appendOutput("Error: Can't continue\n");
			getCheckBox().setClickable(false);
			return;
		}
		configCheck();
	}

	private CheckBox getCheckBox() {
		return (CheckBox) findViewById(R.id.checkBox);
	}

	private ScrollView getScrollView() {
		return (ScrollView) findViewById(R.id.scrollView);
	}

	private TextView getTextViewOutput() {
		return (TextView) findViewById(R.id.textViewOutput);
	}

	private final OnClickListener onCheckBoxClick = new OnClickListener() {
		public void onClick(final View v) {
			final CheckBox CheckBox = (CheckBox) v;
			CheckBox.setClickable(false);
			getTextViewOutput().setText("");
			editConfig(CheckBox.isChecked());
			configCheck();
		}
	};

	private void appendOutput(final String str) {
		final TextView TextViewOutput = getTextViewOutput();
		TextViewOutput.append(str);

		final ScrollView ScrollView = getScrollView();
		ScrollView.post(new Runnable() {
			public void run() {
				ScrollView.fullScroll(View.FOCUS_DOWN);
			}
		});
	}

	private boolean programCheck() {
		appendOutput("Checking for required programs:\n");

		appendOutput("\tbusybox... ");
		if (doExec("which busybox", false) == 0)
			appendOutput("yes\n");
		else {
			appendOutput("no\n");
			return false;
		}

		appendOutput("\tsu... ");
		if (doExec("which su", false) == 0)
			appendOutput("yes\n");
		else {
			appendOutput("no\n");
			return false;
		}

		return true;
	}

	private void configCheck() {
		final CheckBox CheckBox = getCheckBox();

		appendOutput("Checking build.prop...\n");
		if (doExec("grep -q '^media.a1026.enableA1026=1$' /system/build.prop",
				false) == 0) {
			CheckBox.setChecked(true);
			CheckBox.setOnClickListener(onCheckBoxClick);
			CheckBox.setClickable(true);
			appendOutput("OK - A1026 is enabled\n");
		} else if (doExec(
				"grep -q '^media.a1026.enableA1026=0$' /system/build.prop",
				false) == 0) {
			CheckBox.setChecked(false);
			CheckBox.setOnClickListener(onCheckBoxClick);
			CheckBox.setClickable(true);
			appendOutput("OK - A1026 is disabled\n");
		} else {
			CheckBox.setClickable(false);
			appendOutput("Error: Unsupported Device\n");
		}
	}

	private boolean configIsWritable() {
		appendOutput("Checking whether build.prop is writable... ");

		if (doExec("touch /system/build.prop", true) == 0) {
			appendOutput("yes\n");
			return true;
		}

		appendOutput("no\n");
		return false;
	}

	private boolean remountSystem(final boolean rw) {
		if (rw) {
			appendOutput("Remounting /system read-write... ");
			if (doExec("mount -o remount,rw /system", true) == 0) {
				appendOutput("OK\n");
				return true;
			}

			appendOutput("failed\n");
			return false;
		}

		appendOutput("Remounting /system read-only... ");
		if (doExec("mount -o remount,ro /system", true) == 0) {
			appendOutput("OK\n");
			return true;
		}

		appendOutput("Failed\n");
		return false;
	}

	private void editConfig(final boolean enable) {
		boolean remounted = false;

		if (!configIsWritable())
			if (remountSystem(true)) {
				remounted = true;
				if (!configIsWritable()) {
					appendOutput("Error: Failed to write build.prop\n");
					remountSystem(false);
					return;
				}
			} else {
				appendOutput("Error: Failed to write build.prop\n");
				return;
			}

		if (enable) {
			appendOutput("Enabling A1026... ");
			if (doExec(
					"sed -i '/^media.a1026.enableA1026=/s:=0:=1:' /system/build.prop",
					true) == 0)
				appendOutput("OK\n");
			else
				appendOutput("failed\n");
		} else {
			appendOutput("Disabling A1026... ");
			if (doExec(
					"sed -i '/^media.a1026.enableA1026=/s:=1:=0:' /system/build.prop",
					true) == 0)
				appendOutput("OK\n");
			else
				appendOutput("failed\n");
		}

		if (remounted)
			remountSystem(false);
		else
			appendOutput("Leaving /system read-write (as we found it)\n");
	}

	private int doExec(final String command, final boolean su) {
		int status = 0;
		Process p = null;
		DataOutputStream dos = null;

		try {
			if (su)
				p = Runtime.getRuntime().exec("su");
			else
				p = Runtime.getRuntime().exec("sh");

			dos = new DataOutputStream(p.getOutputStream());
			dos.writeBytes("exec " + command + "\n");
			dos.flush();
			status = p.waitFor();
		} catch (final Exception e) {
			status = 1;
		} finally {
			try {
				if (dos != null)
					dos.close();
				p.destroy();
			} catch (final Exception e) {
				;
			}
		}
		return status;
	}
}
