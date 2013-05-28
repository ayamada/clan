package jp.ne.tir.clan;

/*
 * このモジュールは、clojureの初期化にかかる時間を
 * ごまかす為のブートエフェクト類を出すモジュールです。
 *
 * TODO:
 * BootLoaderのコンストラクタの引数で以下を設定/変更できるようにする事
 * - ジングルファイル
 * - ロゴファイル
 * - ロゴファイルの表示処理
 * - 背景色
 * - コンソールのフォント、色
 * - コンソールに表示する文字列
 *   - 「loading」「...」「done」の各文字列
 *   - 回転カーソル表示
 */

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.utils.CharArray;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.*;
import java.util.concurrent.Callable;
import java.lang.StringBuilder;

// ApplicationListenerCache
class ALC {
	static ApplicationListener al = null;
	static boolean reserveNextAlClear = false;
}

public class BootLoader implements ApplicationListener {
	static final float fadeSec = 1.0f; // fadein/fadeoutにかける秒数
	static final float[] bgColorRGB = {0f, 0f, 0f}; // 背景色
	static final float[] fgColorRGB = {0.5f, 0.5f, 0.5f}; // 文字色

	public enum Phase {
		NONE, // 初期状態
		JINGLE, // jingle.oggを再生する
		CLJINIT, // clojureの初期化を行う
		FADEIN, // ロゴをフェードイン表示する
		NEKOINIT, // nekoの初期化を行う
		CALINIT, // calの初期化を行う
		JINGLEWAIT, // ジングル終了を待つ
		FADEOUT, // ロゴをフェードアウト消去する
		CALSTART, // calへ制御を渡す
		CALEXEC, // cal実行状態。基本的にここからはもう動かない
	};

	private class Console {
		private LinkedList<StringBuilder> bufs;
		private int bufLen;
		private BitmapFont font;
		private SpriteBatch batch;
		private boolean enableCursor;
		private LinkedList<BitmapFontCache> fontCaches;
		private BitmapFontCache fontCache;
		private String cursor;
		private float lineHeight;
		private boolean updateLast;
		private boolean updateAll;

		public Console (BitmapFont f, SpriteBatch b) {
			font = f;
			batch = b;
			enableCursor = true;
			cursor = " *";
			bufs = new LinkedList<StringBuilder>();
			bufs.addFirst(new StringBuilder(cursor));
			bufLen = bufs.getFirst().length();
			lineHeight = font.getLineHeight();
			fontCaches = new LinkedList<BitmapFontCache>();
			fontCaches.addFirst(new BitmapFontCache(font));
			updateLast = true;
			updateAll = true;
		}
		private String getConsoleCursorStr (int num) {
			switch (num) {
				case 0:
					return " |";
				case 1:
					return " /";
				case 2:
					return " -";
				case 3:
					return " \\";
				default:
					throw new RuntimeException("assert getConsoleCursorStr: "+String.valueOf(num));
			}
		}

		public void push (CharSequence cs) {
			if (enableCursor) bufs.getFirst().setLength(bufLen-2);
			StringBuilder line = new StringBuilder(cs);
			if (enableCursor) line.append(cursor);
			bufLen = line.length();
			bufs.addFirst(line);
			fontCaches.addFirst(new BitmapFontCache(font));
			updateAll = true;
		}

		public void appendLatest (CharSequence cs) {
			StringBuilder line = bufs.getFirst();
			if (enableCursor) line.setLength(bufLen-2);
			line.append(cs);
			if (enableCursor) line.append(cursor);
			bufLen = line.length();
			updateLast = true;
		}
		public void draw () {
			if (enableCursor) {
				long now = TimeUtils.millis();
				String c = getConsoleCursorStr((int)((now/500) % 4));
				if (!cursor.equals(c)) {
					cursor = c;
					StringBuilder line = bufs.getFirst();
					int length = line.length();
					line.replace(length-2, length, cursor);
					updateLast = true;
				}
			}
			/* コンソール内容はログにのみ表示し、表には"loading ... "しか出さない
			if (updateLast || updateAll) {
				int max = updateAll ? bufs.size() : 1;
				int x = 8;
				int y = 8;
				for (int i = 0; i < max; i++) {
					StringBuilder line = bufs.get(i);
					BitmapFontCache fc = fontCaches.get(i);
					// 古い行ほど色を薄くする
					float cVal = (float)Math.pow(0.85, i);
					fc.setColor(fgColorRGB[0], fgColorRGB[1], fgColorRGB[2], cVal);
					y += lineHeight;
					fc.setText(line, x, y);
				}
				updateLast = false;
				updateAll = false;
			}
			int max = fontCaches.size();
			for (int i = 0; i < max; i++) {
				fontCaches.get(i).draw(batch);
			}
			*/
			if (updateLast || updateAll) {
				BitmapFontCache fc = fontCaches.get(0);
				fc.setColor(fgColorRGB[0], fgColorRGB[1], fgColorRGB[2], 1);
				int x = 8;
				int y = 8;
				y += lineHeight;
				String line;
				// TODO: あとでカスタマイズしやすいようにしておく事
				// TODO: 20%とか現在の状況をパーセント表示できないか考える
				if (enableCursor) {
					line = "loading ..." + cursor;
				}
				else {
					line = "loading ... done.";
				}
				fc.setText(line, x, y);
			}
			fontCaches.get(0).draw(batch);
		}
		public void dump () {
			// it is destractive!
			Gdx.app.debug("CBL", "*** CBL LOG DUMP start ***");
			bufs.removeLast(); // oldest line is empty
			while (bufs.size() != 0) {
				Gdx.app.debug("CBL", bufs.removeLast().toString());
			}
			Gdx.app.debug("CBL", "*** CBL LOG DUMP done ***");
		}
		public void hideCursor () {
			if (enableCursor) bufs.getFirst().setLength(bufLen-2);
			enableCursor = false;
			bufLen = bufs.getFirst().length();
			updateLast = true;
		}
	}

	private static final String assetDir = "assets"; // logo.png se.wav 等のあるdir
	private SpriteBatch batch;
	private Texture logo; // これは動的生成の為、pause()→resume()の度にdispose()と再生成を行わなくてはならない
	private float logoFade;
	private BitmapFont font;
	private OrthographicCamera camera;
	private Music jingle;
	private int screenWidth; // 実描画領域幅
	private int screenHeight; // 実描画領域高
	private Phase phase;
	private int phaseStep;
	private float phaseSec;
	private Console console;
	//private Preferences prefs;
	private Runnable cljInit;
	private Runnable nekoInit;
	private Callable<ApplicationListener> spawner;
	private ApplicationListener cal;
	private boolean nowPreparing;
	private Throwable lastError;
	private boolean fadeinFlag;

	/* 引数は以下の通り。実行順もこの順。
	 * cal.create(), cal.resize()の実行はこの後(というかフェードアウト後)。
	 * 「別スレッドで実行可能な(重い)初期化処理(clojure初期化を想定)」
	 * 「メインスレッドでしか実行できない(軽い)初期化処理(neko初期化を想定)」
	 * 「別スレッドで new CAL() し、その結果を返り値とする」
	 */
	public BootLoader (Runnable cljInitAnotherThread, Runnable nekoInitMainThread, Callable<ApplicationListener> spawn) {
		jingle = null;
		phase = Phase.NONE;
		phaseStep = 0;
		phaseSec = 0.0F;
		logoFade = 0.0F;
		cljInit = cljInitAnotherThread;
		nekoInit = nekoInitMainThread;
		spawner = spawn;
		cal = null;
		nowPreparing = false;
		fadeinFlag = false;
	}
	
	private FileHandle solveFileHandle (String file) {
		return Gdx.files.internal(assetDir+"/"+file);
	}
	private int liftPowerOfTwo (int n) {
		int result = 1;
		while (result < n) {
			result *= 2;
		}
		return result;
	}
	private Pixmap solveLogoPixmap () {
		FileHandle fh = solveFileHandle("cbl_logo.png");
		Pixmap result;
		if (fh.exists()) {
			Pixmap p = new Pixmap(fh);
			int width = liftPowerOfTwo(p.getWidth());
			int height = liftPowerOfTwo(p.getHeight());
			result = new Pixmap(width, height, p.getFormat());
			int x = (width - p.getWidth()) / 2;
			int y = (height - p.getHeight()) / 2;
			result.drawPixmap(p, x, y, 0, 0, p.getWidth(), p.getHeight());
			p.dispose();
		}
		else {
			//throw new RuntimeException("cbl_logo.png not found");
			return null;
		}
		return result;
	}
	private Texture solveLogoTexture () {
		Pixmap logoPixmap = solveLogoPixmap();
		if (logoPixmap == null) { return null; }
		Texture t = new Texture(logoPixmap);
		logoPixmap.dispose();
		return t;
	}
	private BitmapFont solveLogoFont () {
		FileHandle fntFh = solveFileHandle("cbl_font.fnt");
		FileHandle pngFh = solveFileHandle("cbl_font.png");
		BitmapFont font;
		if (fntFh.exists() && pngFh.exists()) {
			font = new BitmapFont(fntFh, pngFh, false);
		}
		else {
			font = new BitmapFont();
		}
		return font;
	}

	private Music solveJingle () {
		FileHandle jingleFh = solveFileHandle("cbl_jingle.ogg");
		Music jingle;
		if (jingleFh.exists()) {
			jingle = Gdx.audio.newMusic(jingleFh);
		}
		else {
			jingle = null;
		}
		return jingle;
	}

	private boolean isClojureStarted () {
		return (phase == Phase.CALEXEC);
	}

	@Override
	public void create () {
		if (ALC.reserveNextAlClear) {
			ALC.reserveNextAlClear = false;
			ALC.al = null;
		}
		if (null != ALC.al) { ALC.al.create(); return; }
		//if (Info.debug) Gdx.app.setLogLevel(Application.LOG_DEBUG);
		logo = solveLogoTexture();
		font = solveLogoFont();
		batch = new SpriteBatch();
		camera = new OrthographicCamera();
		jingle = solveJingle();
		console = new Console(font, batch);
		//prefs = Gdx.app.getPreferences(
		//		"CBL-"+Info.projectGroupId
		//		+"-"+Info.projectArtifactId
		//		+"-"+Info.projectClassifier);
	}

	private void disposeTrue () {
		// NB: 必ず create() と対になっている必要がある！
		// console は dispose() 不要
		if (jingle != null) jingle.dispose();
		// camera には dispose() はない
		batch.dispose();
		font.dispose();
	}

	@Override
	public void resize (int width, int height) {
		if (null != ALC.al) { ALC.al.resize(width, height); return; }
		if (isClojureStarted()) { cal.resize(width, height); return; }
		screenWidth = width;
		if (screenWidth < 128) screenWidth = 128;
		screenHeight = height;
		if (screenHeight < 128) screenHeight = 128;
		camera.setToOrtho(false, screenWidth, screenHeight);
		camera.update();
		batch.setProjectionMatrix(camera.combined);
	}

	@Override
	public void render () {
		if (null != ALC.al) { ALC.al.render(); return; }
		if (isClojureStarted()) { cal.render(); return; }
		try {
			float delta = Gdx.graphics.getDeltaTime();
			phaseSec += delta;

			Gdx.gl.glClearColor(bgColorRGB[0], bgColorRGB[1], bgColorRGB[2], 1f);
			Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
			batch.begin();
			console.draw();
			// フェードイン/アウト色設定
			if (fadeinFlag) {
				if (phaseSec < fadeSec) {
					logoFade = phaseSec / fadeSec;
				}
				else {
					logoFade = 1.0F;
					fadeinFlag = false;
				}
			}
			// ロゴを画面中央に出す
			batch.setColor(1, 1, 1, logoFade);
			if (null != logo) {
				batch.draw(logo, (screenWidth-logo.getWidth())/2, (screenHeight-logo.getHeight())/2);
			}
			batch.end();

			// CBL内のフェーズ別の処理にディスパッチする
			phaseStep++;
			switch(phase) {
				case NONE: phaseNone(delta); break;
				case JINGLE: phaseJingle(delta); break;
				case CLJINIT: phaseCljInit(delta); break;
				case FADEIN: phaseFadein(delta); break;
				case NEKOINIT: phaseNekoInit(delta); break;
				case CALINIT: phaseCalInit(delta); break;
				case JINGLEWAIT: phaseJingleWait(delta); break;
				case FADEOUT: phaseFadeout(delta); break;
				case CALSTART: phaseCalStart(delta); break;
				case CALEXEC: phaseCalExec(delta); break;
				default: throw new RuntimeException("assert phase dispatch");
			}
		}
		catch (RuntimeException e) {
			console.dump();
			throw e;
		}
	}

	@Override
	public void pause () {
		if (null != ALC.al) { ALC.al.pause(); return; }
		if (isClojureStarted()) { cal.pause(); return; }
		if (null != logo) { logo.dispose(); }
	}

	@Override
	public void resume () {
		if (null != ALC.al) { ALC.al.resume(); return; }
		if (isClojureStarted()) { cal.resume(); return; }
		logo = solveLogoTexture();
	}

	@Override
	public void dispose () {
		if (null != ALC.al) { ALC.al.dispose(); return; }
		// clojure起動後は、clojure側をdispose()する
		if (isClojureStarted()) cal.dispose();
		// そうでない場合は、まだCBLのdisposeTrue()が実行されてないので、実行する
		else disposeTrue();
	}

	// TODO: リストとか使って、コードに継続を埋め込まなくてすむようにする(現状だと順序変更すると書き換えする量が多くて面倒)
	private void phaseNone (float delta) {
		if (phaseStep == 1) {
			console.push("================================");
			console.push("CLAN-BOOT-LOADER start.");
			console.push("================================");
			/*
			console.push("================================");
			console.push("CLAN-BOOT-LOADER Ver."+Info.clanCblVersion+" start.");
			console.push("CLAN information:");
			if (Info.debug) {
				console.push("  * debug: ON");
				console.push("  * build-date: "+Info.buildDate);
				console.push("  * build-environment: "+Info.buildEnv);
				console.push("  * javac-version: "+Info.buildCompilerVersion);
			}
			console.push("  * build-number: "+Info.buildNumber);
			console.push("  * include: clojure-"+Info.clanClojureVersion);
			console.push("  * include: libgdx-"+Info.clanLibgdxVersion);
			if (Gdx.app.getType().equals(Application.ApplicationType.Android)) {
				console.push("  * include: android-"+Info.clanAndroidVersion);
				console.push("  * include: neko-"+Info.clanNekoVersion);
			}
			console.push("================================");
			*/
		}
		else {
			phase = Phase.JINGLE; phaseStep = 0;
		}
	}
	private void phaseJingle (float delta) {
		if (phaseStep == 1) console.push("play jingle ...");
		else if (0.1f < phaseSec) {
			if (jingle == null) {
				console.appendLatest(" jingle not found, skipped.");
			}
			//else if (prefs.getBoolean("PLAY_JINGLE", true)) {
			else if (true) {
				jingle.play();
				console.appendLatest(" start.");
			}
			else {
				console.appendLatest(" muted by preferences.");
			}
			phase = Phase.CLJINIT; phaseStep = 0;
		}
	}
	private void phaseCljInit (float delta) {
		if (phaseStep == 1) console.push("initializing clojure ...");
		else if (phaseStep == 2) {
			if (cljInit == null) {
				console.appendLatest(" skipped.");
			}
			else {
				nowPreparing = true;
				new Thread(new Runnable() { @Override public void run() {
					cljInit.run(); // 別スレッドでclojureの初期化を行う
					nowPreparing = false;
				}}).start();
				console.appendLatest(" start.");
			}
			phase = Phase.FADEIN; phaseStep = 0;
		}
	}
	private void phaseFadein (float delta) {
		console.push("fade-in splash-screen ...");
		phaseSec = 0.0F;
		fadeinFlag = true;
		console.appendLatest(" start.");
		phase = Phase.NEKOINIT; phaseStep = 0;
	}
	private void phaseNekoInit (float delta) {
		if (cljInit == null) {
			console.push("initializing neko ...");
			console.appendLatest(" skipped.");
			phase = Phase.CALINIT; phaseStep = 0;
		}
		else {
			if (phaseStep == 1) console.push("waiting to initialize clojure ...");
			if (!nowPreparing) {
				console.appendLatest(" done.");
				console.push("initializing neko ...");
				if (nekoInit != null) nekoInit.run(); // メインスレッドでneko.compilationのinit実行
				console.appendLatest(" done.");
				phase = Phase.CALINIT; phaseStep = 0;
			}
		}
	}
	private void phaseCalInit (float delta) {
		if (phaseStep == 1) console.push("constructing CAL ...");
		else if (phaseStep == 2) {
			nowPreparing = true;
			new Thread(new Runnable() { @Override public void run() {
				try {
					cal = spawner.call();
					nowPreparing = false;
				}
				catch (Throwable e) {
					lastError = e;
					cal = null;
					nowPreparing = false;
				}
			}}).start();
		}
		else {
			if (!nowPreparing) {
				if (cal == null) {
					Gdx.app.error("BootLoader", lastError.getMessage());
					lastError.printStackTrace();
					throw new RuntimeException("assert cal");
				}
				console.appendLatest(" done.");
				phase = Phase.JINGLEWAIT; phaseStep = 0;
			}
			else {
				try { Thread.sleep(100); } catch (InterruptedException e) {}
			}
		}
	}
	private void phaseJingleWait (float delta) {
		if (phaseStep == 1) console.push("wait finish jingle ...");
		else {
			if (jingle == null) {
				console.appendLatest(" jingle not found, skipped.");
				phase = Phase.FADEOUT; phaseStep = 0;
			}
			else if (!jingle.isPlaying()) {
				console.appendLatest(" done.");
				phase = Phase.FADEOUT; phaseStep = 0;
			}
			else {
				try { Thread.sleep(100); } catch (InterruptedException e) {}
			}
		}
	}
	private void phaseFadeout (float delta) {
		if (phaseStep == 1) {
			console.push("fade-out splash-screen ...");
			fadeinFlag = false;
			phaseSec = 0.0F;
			console.hideCursor(); // was changed by display log timing
		}
		else if (phaseSec < fadeSec) {
			logoFade = 1.0F - phaseSec / fadeSec;
		}
		else {
			logoFade = 0.0F;
			console.appendLatest(" done.");
			//console.hideCursor(); // was changed by display log timing
			phase = Phase.CALSTART; phaseStep = 0;
		}
	}
	private void phaseCalStart (float delta) {
		if (phaseStep < 5) {}
		else {
			// NB: ここは元々postRunnable()に渡していたが、postRunnable()に渡してしまうと(render()で行っている)例外の補足ができなくなるので、この中で実行するように変更した。
			new Runnable() { @Override public void run() {
				// この段階でCBL内で使用したリソースのdispose()を行う。
				// ここ以降は処理の分割を行ってはならない！
				console.push("dispose CBL resources ...");
				disposeTrue();
				console.appendLatest(" done.");

				// 最後の最後で、create()とresize()を呼ぶ
				console.push("call CAL.create() ...");
				cal.create();
				console.appendLatest(" done.");
				console.push("call CAL.resize() ...");
				cal.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
				console.appendLatest(" done.");
				console.push("all done. pass control to CAL, goodbye.");

				// デバッグ時のみ、ここまでのコンソール内容をログに出力する
				/*
				if (Info.debug) {
					console.dump();
				}
				*/
				// ついでにGCしておく
				System.gc();
			}}.run();
			// calに制御を渡すフラグを立て、抜ける
			ALC.al = cal;
			phase = Phase.CALEXEC; phaseStep = 0;
		}
	}
	private void phaseCalExec (float delta) {
		// CALEXECになったら制御をcalに渡すので、ここには決して来ない
		throw new RuntimeException("assert calexec");
	}
}
