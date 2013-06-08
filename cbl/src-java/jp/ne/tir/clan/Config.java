package jp.ne.tir.clan;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ApplicationListener;
import java.util.concurrent.Callable;

/*
 * TODO:
 * BootLoaderのコンストラクタの引数で以下を設定/変更できるようにする事
 * - ロゴファイルの表示処理
 */

public class Config {
	public float fadeSec = 1.0f; // fadein/fadeoutにかける秒数
	public float[] bgColorRGB = {0f, 0f, 0f}; // 背景色
	public float[] fgColorRGB = {0.5f, 0.5f, 0.5f}; // 文字色

	public String logoPath = "assets/cbl_logo.png";
	public String fontPath = "assets/cbl_font.fnt";
	public String jinglePath = "assets/cbl_jingle.ogg";

	public boolean isDisplayLog = false;
	public String loadingStr = "loading ...";
	public String doneStr = " done.";

	public Runnable cljInit;
	public Runnable nekoInit;
	public Callable<ApplicationListener> spawner;

	public Config () {}
}
