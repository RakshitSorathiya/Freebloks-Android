package de.saschahlusiak.freebloks.game;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import android.app.*;
import android.bluetooth.BluetoothSocket;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import com.google.firebase.analytics.FirebaseAnalytics;
import de.saschahlusiak.freebloks.BuildConfig;
import de.saschahlusiak.freebloks.Global;
import de.saschahlusiak.freebloks.R;
import de.saschahlusiak.freebloks.controller.GameMode;
import de.saschahlusiak.freebloks.controller.JNIServer;
import de.saschahlusiak.freebloks.controller.PlayerData;
import de.saschahlusiak.freebloks.controller.SpielClient;
import de.saschahlusiak.freebloks.controller.SpielClientInterface;
import de.saschahlusiak.freebloks.controller.Spielleiter;
import de.saschahlusiak.freebloks.donate.DonateActivity;
import de.saschahlusiak.freebloks.lobby.ChatEntry;
import de.saschahlusiak.freebloks.lobby.LobbyDialog;
import de.saschahlusiak.freebloks.model.Player;
import de.saschahlusiak.freebloks.model.Spiel;
import de.saschahlusiak.freebloks.model.Stone;
import de.saschahlusiak.freebloks.model.Turn;
import de.saschahlusiak.freebloks.network.NET_CHAT;
import de.saschahlusiak.freebloks.network.NET_SERVER_STATUS;
import de.saschahlusiak.freebloks.network.NET_SET_STONE;
import de.saschahlusiak.freebloks.network.Network;
import de.saschahlusiak.freebloks.preferences.FreebloksPreferences;
import de.saschahlusiak.freebloks.view.Freebloks3DView;
import de.saschahlusiak.freebloks.view.effects.BoardStoneGlowEffect;
import de.saschahlusiak.freebloks.view.effects.Effect;
import de.saschahlusiak.freebloks.view.effects.EffectSet;
import de.saschahlusiak.freebloks.view.effects.StoneFadeEffect;
import de.saschahlusiak.freebloks.view.effects.StoneRollEffect;
import de.saschahlusiak.freebloks.view.model.Intro;
import de.saschahlusiak.freebloks.view.model.Sounds;
import de.saschahlusiak.freebloks.view.model.Theme;
import de.saschahlusiak.freebloks.view.model.ViewModel;
import de.saschahlusiak.freebloks.view.model.Intro.OnIntroCompleteListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.StrictMode;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import io.fabric.sdk.android.Fabric;

public class FreebloksActivity extends BaseGameActivity implements ActivityInterface, SpielClientInterface, OnIntroCompleteListener {
	static final String tag = FreebloksActivity.class.getSimpleName();

	static final int DIALOG_GAME_MENU = 1;
	static final int DIALOG_LOBBY = 2;
	static final int DIALOG_QUIT = 3;
	static final int DIALOG_RATE_ME = 4;
	static final int DIALOG_JOIN = 5;
	static final int DIALOG_PROGRESS = 6;
	static final int DIALOG_CUSTOM_GAME = 7;
	static final int DIALOG_NEW_GAME_CONFIRMATION = 8;
	static final int DIALOG_SINGLE_PLAYER = 10;

	static final int REQUEST_FINISH_GAME = 1;

	static final int NOTIFICATION_GAME_ID = 1;

	public static final String GAME_STATE_FILE = "gamestate.bin";


	Freebloks3DView view;
	SpielClient client = null;
	SpielClientThread spielthread = null;
	Vibrator vibrator;
	boolean vibrate_on_move;
	boolean show_notifications;
	boolean undo_with_back;
	boolean hasActionBar;
	NET_SERVER_STATUS lastStatus;
	Menu optionsMenu;
	ViewGroup statusView;
	NotificationManager notificationManager;
	Notification multiplayerNotification;

	ConnectTask connectTask;

	String clientName;
	int difficulty;
	GameMode gamemode;
	int fieldsize;

	ImageButton chatButton;
	ArrayList<ChatEntry> chatEntries;

	SharedPreferences prefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(tag, "onCreate");

		if (BuildConfig.DEBUG) {
	         StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				 .detectCustomSlowCalls()
				 .detectNetwork()
				 .penaltyDeath()
				 .build());
	         StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				 .detectLeakedSqlLiteObjects()
				 .detectLeakedClosableObjects()
				 .detectActivityLeaks()
				 .penaltyLog()
//				 .penaltyDeath()
				 .build());

	    }

		Crashlytics crashlyticsKit = new Crashlytics.Builder()
			.core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
			.build();

		Fabric.with(this, crashlyticsKit);

		Log.d(tag, "nativeLibraryDir=" + getApplicationInfo().nativeLibraryDir);


		/* tablets/phone with ICS may or may not have physical buttons. Show action bar if mising */
		ViewConfiguration viewConfig = ViewConfiguration.get(this);
		/* we need the action bar if we don't have a menu key */
		hasActionBar = !viewConfig.hasPermanentMenuKey();

		if (!hasActionBar)
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		else
			requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

		super.onCreate(savedInstanceState);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
	        Window w = getWindow();
	        
	        w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
//	        w.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
	    }

		if (hasActionBar) {
			// failsafe, there might be Android versions >= 3.0 without an actual ActionBar
			if (getActionBar() == null)
				hasActionBar = false;
		}
		
		if (hasActionBar) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			} else {
				getActionBar().setIcon(android.R.drawable.ic_dialog_dialer);
			}

//			getActionBar().setDisplayShowHomeEnabled(true);
//			getActionBar().setDisplayUseLogoEnabled(false);
//			getActionBar().setBackgroundDrawable(new ColorDrawable(Color.argb(104, 0, 0, 0)));
			getActionBar().setBackgroundDrawable(new ColorDrawable(0));
			getActionBar().setDisplayShowTitleEnabled(false);
		}

		setContentView(R.layout.main_3d);

		prefs = PreferenceManager.getDefaultSharedPreferences(FreebloksActivity.this);
		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		view = findViewById(R.id.board);
		view.setActivity(this);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			if (prefs.getBoolean("immersive_mode", true))
				view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
		}


		statusView = findViewById(R.id.currentPlayerLayout);
		vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		chatButton = findViewById(R.id.chatButton);
		chatButton.setVisibility(View.INVISIBLE);
		chatButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				chatButton.clearAnimation();
				showDialog(DIALOG_LOBBY);
			}
		});
		if (savedInstanceState != null)
			chatEntries = (ArrayList<ChatEntry>)savedInstanceState.getSerializable("chatEntries");
		else
			chatEntries = new ArrayList<>();

		newCurrentPlayer(-1);

		RetainedConfig config = (RetainedConfig)getLastNonConfigurationInstance();
		if (config != null) {
			spielthread = config.clientThread;
			lastStatus = config.lastStatus;
			view.model.soundPool = config.soundPool;
			view.model.intro = config.intro;
			connectTask = config.connectTask;
			if (connectTask != null)
				connectTask.setActivity(this);
			if (view.model.intro != null)
				view.model.intro.setModel(view.model, this);
			canresume = true;
			chatButton.setVisibility((lastStatus != null && lastStatus.clients > 1) ? View.VISIBLE : View.INVISIBLE);
		}
		if (savedInstanceState != null) {
			view.setScale(savedInstanceState.getFloat("view_scale", 1.0f));
			showRateDialog = savedInstanceState.getBoolean("showRateDialog", false);
		} else {
			view.setScale(prefs.getFloat("view_scale", 1.0f));
			showRateDialog = RateAppDialog.checkShowRateDialog(this);

			long starts = prefs.getLong("rate_number_of_starts", 0);

			if (!Global.IS_VIP && starts == Global.DONATE_STARTS) {
				Intent intent = new Intent(this, DonateActivity.class);
				startActivity(intent);
			}
		}

		if (view.model.soundPool == null)
			view.model.soundPool = new Sounds(getApplicationContext());

		clientName = prefs.getString("player_name", null);
		difficulty = prefs.getInt("difficulty", GameConfiguration.DEFAULT_DIFFICULTY);
		gamemode = GameMode.from(prefs.getInt("gamemode", GameMode.GAMEMODE_4_COLORS_4_PLAYERS.ordinal()));
		fieldsize = prefs.getInt("fieldsize", Spiel.DEFAULT_BOARD_SIZE);

		if (spielthread != null) {
			/* we just rotated and got *hot* objects */
			client = spielthread.getClient();
			client.addClientInterface(this);
			view.setSpiel(client, client.spiel);
			newCurrentPlayer(client.spiel.current_player());
		} else if (savedInstanceState == null) {
			if (prefs.getBoolean("show_animations", true) && ! prefs.getBoolean("skip_intro", false)) {
				view.model.intro = new Intro(getApplicationContext(), view.model, this);
				newCurrentPlayer(-1);
			} else
				OnIntroCompleted();
		}

		statusView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (view.model.intro != null)
					view.model.intro.cancel();
			}
		});

		findViewById(R.id.myLocation).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				view.model.board.resetRotation();
			}
		});


		final Animation a = new TranslateAnimation(0, 8, 0, 0);
		a.setInterpolator(new CycleInterpolator(2));
		a.setDuration(500);
		Runnable r = new Runnable() {
			@Override
			public void run() {
				if (view == null)
					return;
				boolean local = false;
				View t = findViewById(R.id.currentPlayer);
				t.postDelayed(this, 5000);

				if (client != null && client.spiel != null)
					local = client.spiel.is_local_player();
				if (!local)
					return;

				t.startAnimation(a);
			}
		};
		findViewById(R.id.currentPlayer).postDelayed(r, 1000);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		Log.d(tag, "onRestoreInstanceState (bundle=" + savedInstanceState + ")");

		if (spielthread == null) {
			if (!readStateFromBundle(savedInstanceState)) {
				canresume = false;
				newCurrentPlayer(-1);
			} else {
				canresume = true;
			}
		}
	}

	boolean canresume = false;
	boolean showRateDialog = false;

	@Override
	public void OnIntroCompleted() {
		newCurrentPlayer(-1);
		try {
			if (restoreOldGame()) {
				canresume = true;
			} else {
				canresume = false;
			}
		} catch (Exception e) {
			canresume = false;
			Toast.makeText(FreebloksActivity.this, R.string.could_not_restore_game, Toast.LENGTH_LONG).show();
		}

		if (!canresume || ! prefs.getBoolean("auto_resume", false))
			showDialog(DIALOG_GAME_MENU);

		if (showRateDialog)
			showDialog(DIALOG_RATE_ME);
	}

	@Override
	protected void onDestroy() {
		Log.d(tag, "onDestroy");
		notificationManager.cancelAll();
		notificationManager.cancel(NOTIFICATION_GAME_ID);

		if (connectTask != null) try {
			connectTask.cancel(true);
			connectTask.get();
			connectTask = null;
		} catch (Exception e) {
		//	e.printStackTrace();
		}
		if (spielthread != null) try {
			spielthread.goDown();
			spielthread.join();
			spielthread = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (client != null) {
			client.removeClientInterface(this);
			/* TODO: make attach/detach of view symmetric */
			client.removeClientInterface(view);
		}
		if (view.model.soundPool != null)
			view.model.soundPool.release();
		view.model.soundPool = null;
		view = null;
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Log.d(tag, "onPause");
		if (client != null && client.spiel.isStarted() && !client.spiel.isFinished())
			saveGameState(GAME_STATE_FILE);
		super.onPause();
	}

	@Override
	protected void onResume() {
		Log.d(tag, "onResume");
		super.onResume();
	}

	@Override
	protected void onStop() {
		if (!isFinishing() && client != null && client.isConnected()) {
			if ((lastStatus != null && lastStatus.clients > 1) ||
				(!client.spiel.isStarted()))
				updateMultiplayerNotification(true, null);
		}
		view.onPause();
		Editor editor = prefs.edit();
		editor.putFloat("view_scale", view.getScale());
		editor.apply();
		Log.d(tag, "onStop");
		super.onStop();
	}

	@Override
	protected void onStart() {
		Log.d(tag, "onStart");
		super.onStart();
		view.onResume();

		notificationManager.cancel(NOTIFICATION_GAME_ID);
		multiplayerNotification = null;

		vibrate_on_move = prefs.getBoolean("vibrate", true);
		view.model.soundPool.setEnabled(prefs.getBoolean("sounds", true));
		show_notifications = prefs.getBoolean("notifications", true);
		view.model.showSeeds = prefs.getBoolean("show_seeds", true);
		view.model.showOpponents = prefs.getBoolean("show_opponents", true);
		view.model.showAnimations = Integer.parseInt(prefs.getString("animations", String.format("%d", ViewModel.ANIMATIONS_FULL)));
		view.model.snapAid = prefs.getBoolean("snap_aid", true);
		view.model.immersiveMode = prefs.getBoolean("immersive_mode", true);
		undo_with_back = prefs.getBoolean("back_undo", false);
		clientName = prefs.getString("player_name", null);
		if (clientName != null && clientName.equals(""))
			clientName = null;
		Theme t = Theme.get(this, prefs.getString("theme", "texture_wood"), false);
		view.setTheme(t);

		updateSoundMenuEntry();
		/* update wheel in case showOpponents has changed */
		view.model.wheel.update(view.model.board.getShowWheelPlayer());
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		Log.d(tag, "onRetainNonConfigurationInstance");
		RetainedConfig config = new RetainedConfig();
		config.clientThread = spielthread;
		config.lastStatus = lastStatus;
		config.soundPool = view.model.soundPool;
		config.intro = view.model.intro;
		config.connectTask = connectTask;
		this.connectTask = null;
		view.model.soundPool = null;
		spielthread = null;
		return config;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(tag, "onSaveInstanceState");
		super.onSaveInstanceState(outState);
		outState.putFloat("view_scale", view.getScale());
		outState.putBoolean("showRateDialog", showRateDialog);
		outState.putSerializable("chatEntries", chatEntries);
		writeStateToBundle(outState);
	}

	private void writeStateToBundle(Bundle outState) {
		if (client == null)
			return;
		if (client.spiel == null)
			return;
		synchronized (client) {
			Spielleiter l = client.spiel;
			if (!l.isFinished())
				outState.putSerializable("game", l);
		}
	}

	private boolean readStateFromBundle(Bundle in) {
		try {
			Spielleiter spiel1 = (Spielleiter)in.getSerializable("game");
			if (spiel1 == null)
				return false;
			// don't restore games that have finished; the server would not detach the listener
			if (spiel1.isFinished())
				return false;

			Crashlytics.log("restore from bundle");
			int ret = JNIServer.runServer(spiel1, spiel1.getGameMode().ordinal(), spiel1.width, null, difficulty);
			if (ret != 0) {
				Crashlytics.log("Error starting server: " + ret);
			}

			/* this will start a new SpielClient, which needs to be restored from saved gamestate first */
			final GameConfiguration config = GameConfiguration.builder()
				.difficulty(difficulty)
				.fieldSize(spiel1.width)
				.build();

			final SpielClient client = new SpielClient(spiel1, config);
			client.spiel.setStarted(true);

			connectTask = new ConnectTask(client, false, new Runnable() {
				@Override
				public void run() {
					spielthread = new SpielClientThread(client);
					spielthread.start();
				}
			});
			connectTask.setActivity(this);

			// this call would execute the onPreTask method, which calls through to show the progress
			// dialog. But because performRestoreInstanceState calls restoreManagedDialogs, those
			// dialogs would be overwritten. To mitigate this, we need to defer starting the connectTask
			// until all restore is definitely complete.
			view.post(new Runnable() {
				@Override
				public void run() {
					if (connectTask != null) connectTask.execute((String)null);
				}
			});

			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	long gameStartTime = 0;

	public void startNewGame() {
		if (client != null) {
			// when starting a new game from the options menu, keep previous config
			startNewGame(client.getConfig(), null);
		} else {
			// else start default game
			startNewGame(GameConfiguration.builder().build(), null);
		}
	}

	public void startNewGame(final GameConfiguration config, final Runnable runAfter) {
		newCurrentPlayer(-1);
		if (config.getServer() == null) {
			int ret = JNIServer.runServer(
				null,
				config.getGameMode().ordinal(),
				config.getFieldSize(),
				config.getStones(),
				config.getDifficulty()
			);

			if (ret != 0) {
				Crashlytics.log("Error starting server: " + ret);
			}
		}

		if (spielthread != null)
			spielthread.goDown();
		spielthread = null;
		client = null;

		view.model.clearEffects();
		Spielleiter spiel = new Spielleiter(fieldsize);
		final SpielClient client = new SpielClient(spiel, config);
		spiel.startNewGame(config.getGameMode(), config.getFieldSize(), config.getFieldSize());
		spiel.setAvailableStones(0, 0, 0, 0, 0);

		connectTask = new ConnectTask(client, config.getShowLobby(), new Runnable() {
			@Override
			public void run() {
				spielthread = new SpielClientThread(client);
				spielthread.start();

				if (config.getRequestPlayers() == null)
					client.request_player(-1, clientName);
				else {
					for (int i = 0; i < 4; i++)
						if (config.getRequestPlayers()[i])
							client.request_player(i, clientName);
				}
				if (! config.getShowLobby())
					client.request_start();
				else {
					Bundle b = new Bundle();
					b.putString("server", config.getServer() == null ? "localhost" : config.getServer());
					FirebaseAnalytics.getInstance(FreebloksActivity.this).logEvent("show_lobby", b);
				}

				if (config.getServer() == null) {
					// hosting a local game. start bluetooth bridge.
					BluetoothServer bluetoothServer = new BluetoothServer(new BluetoothServer.OnBluetoothConnectedListener() {
						@Override
						public void onBluetoothClientConnected(BluetoothSocket socket) {
							new BluetoothClientBridge(socket, "localhost", Network.DEFAULT_PORT).start();
						}
					});
					bluetoothServer.start();
					client.addClientInterface(bluetoothServer);
				}

				if (runAfter != null)
					runAfter.run();
			}
		});
		connectTask.setActivity(this);
		connectTask.execute(config.getServer());
	}

	public void establishBluetoothGame(BluetoothSocket socket) throws IOException {
		final GameConfiguration config = GameConfiguration.builder().build();
		newCurrentPlayer(-1);

		if (spielthread != null)
			spielthread.goDown();
		spielthread = null;
		client = null;
		view.model.clearEffects();
		Log.d(tag, "Establishing game with existing bluetooth connection");

		Spielleiter spiel = new Spielleiter(fieldsize);
		spiel.startNewGame(GameMode.GAMEMODE_4_COLORS_4_PLAYERS);
		spiel.setAvailableStones(0, 0, 0, 0, 0);

		client = new SpielClient(spiel, config);
		view.setSpiel(client, spiel);

		client.addClientInterface(this);
		client.setSocket(socket);

		spielthread = new SpielClientThread(client);
		spielthread.start();

		if (config.getRequestPlayers() == null)
			client.request_player(-1, clientName);

		showDialog(FreebloksActivity.DIALOG_LOBBY);

		FirebaseAnalytics.getInstance(this).logEvent("bluetooth_connected", null);
	}

	boolean restoreOldGame() throws Exception {
		try {
			FileInputStream fis = openFileInput(FreebloksActivity.GAME_STATE_FILE);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			Parcel p = Parcel.obtain();
			byte[] b = new byte[1024];
			int bytesRead;
			while ((bytesRead = fis.read(b)) != -1) {
			   bos.write(b, 0, bytesRead);
			}
			fis.close();

			byte[] bytes = bos.toByteArray();
			bos.close();

			Bundle bundle;
			p.unmarshall(bytes, 0, bytes.length);
			p.setDataPosition(0);
			bundle = p.readBundle(FreebloksActivity.class.getClassLoader());
			p.recycle();

			deleteFile(GAME_STATE_FILE);

			if (readStateFromBundle(bundle)) {
				return true;
			} else {
				return false;
			}
		} catch (FileNotFoundException fe) {
			/* signal non-failure if game state file is missing */
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	private void saveGameState(final String filename) {
		final Parcel p = Parcel.obtain();
		Bundle b = new Bundle();
		synchronized (client) {
			writeStateToBundle(b);
			p.writeBundle(b);
		}
		new Thread() {
			public void run() {
				try {
					FileOutputStream fos;
					fos = openFileOutput(filename, Context.MODE_PRIVATE);
					fos.write(p.marshall());
					fos.flush();
					fos.close();
					p.recycle();
				} catch (Exception e) {
					Crashlytics.logException(e);
					e.printStackTrace();
				}
			}
		}.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.game_optionsmenu, menu);
		optionsMenu = menu;

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean local = false;
		if (client != null && client.spiel != null)
			local = client.spiel.is_local_player();

		menu.findItem(R.id.undo).setEnabled(local && lastStatus != null && lastStatus.clients <= 1);
		menu.findItem(R.id.hint).setEnabled(local);
		menu.findItem(R.id.sound_toggle_button).setVisible(hasActionBar);
		updateSoundMenuEntry();

		return super.onPrepareOptionsMenu(menu);
	}

	void updateSoundMenuEntry() {
		boolean on = true;
		if (optionsMenu == null)
			return;
		if (view != null && view.model != null && view.model.soundPool != null)
			on = view.model.soundPool.isEnabled();
		optionsMenu.findItem(R.id.sound_toggle_button).setTitle(on ? R.string.sound_on : R.string.sound_off);
		optionsMenu.findItem(R.id.sound_toggle_button).setIcon(on ? R.drawable.ic_volume_up_white_48dp : R.drawable.ic_volume_off_white_48dp);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_LOBBY:
			if (client == null)
				return null;
			return new LobbyDialog(this, chatEntries);

		case DIALOG_QUIT:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.do_you_want_to_leave_current_game);
			builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					canresume = true;
					showDialog(DIALOG_GAME_MENU);
				}
			});
			builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					arg0.dismiss();
				}
			});
			return builder.create();

		case DIALOG_NEW_GAME_CONFIRMATION:
			builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.do_you_want_to_leave_current_game);
			builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					startNewGame();
				}
			});
			builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					arg0.dismiss();
				}
			});
			return builder.create();

		case DIALOG_RATE_ME:
			return new RateAppDialog(this);

		case DIALOG_GAME_MENU:
			return new GameMenu(this);

		case DIALOG_CUSTOM_GAME:
			return new CustomGameDialog(this, new CustomGameDialog.OnStartCustomGameListener() {
				@Override
				public boolean OnStart(CustomGameDialog dialog) {
					startNewGame(dialog.getConfiguration(), null);
					dismissDialog(DIALOG_CUSTOM_GAME);
					dismissDialog(DIALOG_GAME_MENU);
					return true;
				}
			});

		case DIALOG_JOIN:
			return new JoinDialog(this, new JoinDialog.OnStartCustomGameListener() {
				@Override
				public void setClientName(String name) {
					clientName = name;
				}

				@Override
				public void onJoinGame(String server) {
					startNewGame(GameConfiguration.builder()
							.server(server)
							.showLobby(true)
							.build(),
						null
					);
					dismissDialog(DIALOG_GAME_MENU);
				}

				@Override
				public void onHostGame() {
					startNewGame(GameConfiguration.builder().showLobby(true).build(), null);
					dismissDialog(DIALOG_GAME_MENU);
				}

				@Override
				public void onHostBluetoothGameWithClient(final BluetoothSocket clientSocket) {
					dismissDialog(DIALOG_GAME_MENU);
					startNewGame(GameConfiguration.builder().showLobby(true).build(), new Runnable() {
						@Override
						public void run() {
							new BluetoothClientBridge(clientSocket, "localhost", Network.DEFAULT_PORT).start();
						}
					});
				}

				@Override
				public void onJoinGame(BluetoothSocket socket) {
					// got a connected bluetooth socket to a server
					dismissDialog(DIALOG_GAME_MENU);

					try {
						establishBluetoothGame(socket);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

		case DIALOG_PROGRESS:
			ProgressDialog p = new ProgressDialog(FreebloksActivity.this);
			p.setMessage(getString(R.string.connecting));
			p.setIndeterminate(true);
			p.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			p.setCancelable(true);
			p.setButton(Dialog.BUTTON_NEGATIVE, getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			});
			return p;
		
		case DIALOG_SINGLE_PLAYER:
			ColorListDialog d = new ColorListDialog(this, 
					new ColorListDialog.OnColorSelectedListener() {
						@Override
						public void onColorSelected(ColorListDialog dialog, int color) {
							boolean[] players = new boolean[4];
							players[color] = true;
							onColorsSelected(dialog, players);
						}

						@Override
						public void onRandomColorSelected(ColorListDialog dialog) {
							onColorsSelected(dialog, null);
						}

						@Override
						public void onColorsSelected(ColorListDialog dialog, boolean[] players) {
		            	   gamemode = dialog.getGameMode();
		            	   fieldsize = dialog.getBoardSize();
		            	   final GameConfiguration config = GameConfiguration.builder()
							   .requestPlayers(players)
							   .fieldSize(fieldsize)
							   .gameMode(gamemode)
							   .showLobby(false)
							   .build();
		            	   startNewGame(config, null);

		            	   dialog.dismiss();
						}
		           });
			d.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						showDialog(DIALOG_GAME_MENU);
					}
				});
			
			return d;
			
		default:
			return super.onCreateDialog(id);
		}
	}

	@Override
	protected void onPrepareDialog(int id, final Dialog dialog, Bundle args) {
		switch (id) {
		case DIALOG_LOBBY:
			if (client != null) {
				((LobbyDialog)dialog).setSpiel(client);
				if (lastStatus != null)
					((LobbyDialog)dialog).serverStatus(lastStatus);
				dialog.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface arg0) {
						if (!client.spiel.isStarted() && !client.spiel.isFinished()) {
							FirebaseAnalytics.getInstance(FreebloksActivity.this).logEvent("lobby_close", null);
							canresume = false;
							if (spielthread != null)
								spielthread.goDown();
							spielthread = null;
							client = null;
							showDialog(DIALOG_GAME_MENU);
						}
					}
				});
				if (client.spiel.isStarted()) {
					dialog.setCanceledOnTouchOutside(true);
				} else {
					dialog.setCanceledOnTouchOutside(false);
				}
			} else {
				/* this can happen when the app is saved but purged from memory
				 * upon resume, the open dialog is reopened but the client connection
				 * has to be disconnected. just close the lobby since there is no
				 * connection
				 */
				dialog.dismiss();
				canresume = false;
				showDialog(DIALOG_GAME_MENU);
			}
			break;

		case DIALOG_GAME_MENU:
			GameMenu g = (GameMenu) dialog;
			g.setResumeEnabled(canresume);
			break;

		case DIALOG_JOIN:
			((JoinDialog)dialog).setName(clientName);
			break;

		case DIALOG_CUSTOM_GAME:
			((CustomGameDialog)dialog).prepareCustomGameDialog(difficulty, gamemode, fieldsize);
			break;
			
		case DIALOG_PROGRESS:
			if (connectTask != null)
				dialog.setOnCancelListener(connectTask);
			break;

		case DIALOG_SINGLE_PLAYER:
			ColorListDialog d = (ColorListDialog)dialog;
			d.setGameMode(gamemode);
			break;
		}
		super.onPrepareDialog(id, dialog, args);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;
		switch (item.getItemId()) {
		case android.R.id.home:
			if (client != null && client.isConnected())
				canresume = true;
			else
				canresume = false;

			showDialog(DIALOG_GAME_MENU);
			if (view.model.intro != null)
				view.model.intro.cancel();
			return true;

		case R.id.new_game:
			if (view.model.intro != null)
				view.model.intro.cancel();
			else {
				if (client == null || (client.spiel != null && client.spiel.isFinished()))
					startNewGame();
				else
					showDialog(DIALOG_NEW_GAME_CONFIRMATION);
			}
			return true;

		case R.id.preferences:
			intent = new Intent(this, FreebloksPreferences.class);
			startActivity(intent);
			return true;

		case R.id.sound_toggle_button:
			Editor editor = prefs.edit();
			view.model.soundPool.toggle();
			editor.putBoolean("sounds", view.model.soundPool.isEnabled());
			editor.apply();
			updateSoundMenuEntry();
			Toast.makeText(this, getString(view.model.soundPool.isEnabled() ? R.string.sound_on : R.string.sound_off), Toast.LENGTH_SHORT).show();
			return true;

		case R.id.hint:
			if (client == null)
				return true;
			findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
			findViewById(R.id.movesLeft).setVisibility(View.INVISIBLE);
			view.model.currentStone.stopDragging();
			client.request_hint(client.spiel.current_player());
			return true;

		case R.id.undo:
			if (client == null)
				return true;
			view.model.clearEffects();
			client.request_undo();
			view.model.soundPool.play(view.model.soundPool.SOUND_UNDO, 1.0f, 1.0f);
			return true;

		case R.id.show_main_menu:
			if (client != null && client.spiel.isStarted() && lastStatus != null && lastStatus.clients > 1)
				showDialog(DIALOG_QUIT);
			else {
				if (client != null && client.isConnected())
					canresume = true;
				else
					canresume = false;

				showDialog(DIALOG_GAME_MENU);
				if (view.model.intro != null)
					view.model.intro.cancel();
			}
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_FINISH_GAME:
			if (resultCode == GameFinishActivity.RESULT_NEW_GAME) {
				startNewGame();
			}
			if (resultCode == GameFinishActivity.RESULT_SHOW_MENU) {
				showDialog(DIALOG_GAME_MENU);
			}
			break;

		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void newCurrentPlayer(final int player) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (view == null)
					return;
				if (multiplayerNotification != null)
					updateMultiplayerNotification(false, null);
				boolean local = false;
				int showPlayer = view.model.board.getShowDetailsPlayer();
				if (client != null && client.spiel != null)
					local = client.spiel.is_local_player(player);
				else
					showPlayer = player;

				if (optionsMenu != null) {
					optionsMenu.findItem(R.id.hint).setEnabled(local);
					optionsMenu.findItem(R.id.undo).setEnabled(local && lastStatus != null && lastStatus.clients <= 1);
				}

				findViewById(R.id.progressBar).setVisibility((local || player < 0) ? View.GONE : View.VISIBLE);

				TextView movesLeft, points, status;
				movesLeft = (TextView)findViewById(R.id.movesLeft);
				movesLeft.setVisibility(View.INVISIBLE);
				points = (TextView)findViewById(R.id.points);
				points.setVisibility(View.INVISIBLE);

				status = (TextView)findViewById(R.id.currentPlayer);
				status.clearAnimation();
				findViewById(R.id.myLocation).setVisibility((showPlayer >= 0) ? View.VISIBLE : View.INVISIBLE);
				if (player < 0)
					statusView.setBackgroundColor(Color.rgb(64, 64, 80));

				if (view.model.intro != null)
					status.setText(R.string.touch_to_skip);
				else if (client == null || !client.isConnected())
					status.setText(R.string.not_connected);
				else if (client.spiel.isFinished()) {
					int pl = view.model.board.getShowWheelPlayer();
					if (pl >= 0) {
						int res = Global.PLAYER_BACKGROUND_COLOR_RESOURCE[view.model.getPlayerColor(pl)];
						Player p = client.spiel.getPlayer(pl);
						status.setText("[" + getPlayerName(pl) + "]");
						statusView.setBackgroundColor(getResources().getColor(res));
						points.setVisibility(View.VISIBLE);
						points.setText(getResources().getQuantityString(R.plurals.number_of_points, p.m_stone_points, p.m_stone_points));
						movesLeft.setVisibility(View.VISIBLE);
						movesLeft.setText(getResources().getQuantityString(R.plurals.number_of_stones_left, p.m_stone_count, p.m_stone_count));
					}
				} else if (player >= 0 || showPlayer >= 0) {
					if (showPlayer < 0) {
						int res = Global.PLAYER_BACKGROUND_COLOR_RESOURCE[view.model.getPlayerColor(player)];
						statusView.setBackgroundColor(getResources().getColor(res));
						Player p = client.spiel.getPlayer(player);
						points.setVisibility(View.VISIBLE);
						points.setText(getResources().getQuantityString(R.plurals.number_of_points, p.m_stone_points, p.m_stone_points));
						if (!local)
							status.setText(getString(R.string.waiting_for_color, getPlayerName(player)));
						else {
							status.setText(getString(R.string.your_turn, getPlayerName(player)));

							movesLeft.setVisibility(View.VISIBLE);
							movesLeft.setText(getResources().getQuantityString(R.plurals.player_status_moves, p.m_number_of_possible_turns, p.m_number_of_possible_turns));
						}
					} else {
						int res = Global.PLAYER_BACKGROUND_COLOR_RESOURCE[view.model.getPlayerColor(showPlayer)];
						statusView.setBackgroundColor(getResources().getColor(res));
						Player p = client.spiel.getPlayer(showPlayer);
						points.setVisibility(View.VISIBLE);
						points.setText(getResources().getQuantityString(R.plurals.number_of_points, p.m_stone_points, p.m_stone_points));

						if (p.m_number_of_possible_turns <= 0)
							status.setText("[" + getString(R.string.color_is_out_of_moves, getPlayerName(showPlayer)) + "]");
						else {
							status.setText(getPlayerName(showPlayer));

							movesLeft.setVisibility((local || player < 0) ? View.VISIBLE : View.INVISIBLE);
							movesLeft.setText(getResources().getQuantityString(R.plurals.player_status_moves, p.m_number_of_possible_turns, p.m_number_of_possible_turns));
						}
					}

				} else
					status.setText(R.string.no_player);
			}
		});
	}

	/* we have to store the number of possible turns before and after a stone has been set
	 * to detect blocking of other players */
	private int number_of_possible_turns[] = new int[4];

	@Override
	public void stoneWillBeSet(@NonNull NET_SET_STONE s) {
		for (int i = 0; i < 4; i++)
			number_of_possible_turns[i] = client.spiel.getPlayer(i).m_number_of_possible_turns;
	}

	@Override
	public void stoneHasBeenSet(@NonNull final NET_SET_STONE s) {
		if (client == null)
			return;
		if (view == null)
			return;
		final Spielleiter spiel = client.spiel;
		if (spiel == null)
			return;
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				if (!spiel.is_local_player(s.player)) {
					if (view == null)
						return;
					if (view.model.soundPool == null)
						return;
					view.model.soundPool.play(view.model.soundPool.SOUND_CLICK1, 1.0f, 0.9f + (float)Math.random() * 0.2f);
					vibrate(Global.VIBRATE_SET_STONE);
				}
			}
		});

		for (int i = 0; i < 4; i++) {
			final Player p = spiel.getPlayer(i);
			if (p.m_number_of_possible_turns <= 0 && number_of_possible_turns[i] > 0) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (view != null) {
							Toast.makeText(FreebloksActivity.this, getString(R.string.color_is_out_of_moves, getPlayerName(p.getPlayerNumber())), Toast.LENGTH_SHORT).show();

							if (view.model.soundPool != null)
								view.model.soundPool.play(view.model.soundPool.SOUND_PLAYER_OUT, 0.8f, 1.0f);
							if (view.model.hasAnimations()) {
								int sx, sy;
								sx = spiel.getPlayerStartX(p.getPlayerNumber());
								sy = spiel.getPlayerStartY(p.getPlayerNumber());
								for (int x = 0; x < spiel.width; x++)
									for (int y = 0; y < spiel.height; y++)
										if (spiel.getFieldPlayer(y, x) == p.getPlayerNumber()) {
											boolean effected = false;
											synchronized (view.model.effects) {
												for (int j = 0; j < view.model.effects.size(); j++)
													if (view.model.effects.get(j).isEffected(x, y)) {
														effected = true;
														break;
													}
											}
											if (!effected) {
												final float distance = (float)Math.sqrt((x - sx)*(x - sx) + (y - sy)*(y - sy));
												Effect effect = new BoardStoneGlowEffect(
														view.model,
														view.model.getPlayerColor(p.getPlayerNumber()),
														x,
														y,
														distance);
												view.model.addEffect(effect);
											}
										}
							}
						}
					}
				});
			}
		}
	}

	@Override
	public void hintReceived(@NonNull NET_SET_STONE s) {
		FirebaseAnalytics.getInstance(this).logEvent("hint_received", null);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				findViewById(R.id.progressBar).setVisibility(View.INVISIBLE);
				findViewById(R.id.movesLeft).setVisibility(View.VISIBLE);
			}
		});
	}

	@Override
	public void gameFinished() {
		deleteFile(GAME_STATE_FILE);

		Bundle b = new Bundle();
		b.putString("server", client.getConfig().getServer());
		b.putString("game_mode", client.spiel.getGameMode().toString());
		b.putInt("w", client.spiel.width);
		b.putInt("h", client.spiel.height);
		b.putInt("clients", lastStatus.clients);
		b.putInt("players", lastStatus.player);
		FirebaseAnalytics.getInstance(this).logEvent("game_finished", b);

		/* TODO: play sound on game finish */
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (client == null || client.spiel == null)
					return;
				updateMultiplayerNotification(false, null);
				PlayerData[] data = client.spiel.getResultData();
				new AddScoreTask(getApplicationContext(), client.spiel.getGameMode()).execute(data);

				if (client.spiel == null) {
					Crashlytics.logException(new IllegalStateException("gameFinished, but no game running"));
					return;
				}

				Intent intent = new Intent(FreebloksActivity.this, GameFinishActivity.class);
				intent.putExtra("game", (Serializable)client.spiel);
				intent.putExtra("lastStatus", (Serializable)lastStatus);
				intent.putExtra("clientName", clientName);
				startActivityForResult(intent, REQUEST_FINISH_GAME);
			}
		});
	}

	@Override
	public void chatReceived(@NonNull final NET_CHAT c) {
		String name;
		int player = -1;
		if (lastStatus != null && c.client >= 0) {
			if (lastStatus.isVersion(2))
				for (int i = 0; i < lastStatus.spieler.length; i++)
					if (lastStatus.spieler[i] == c.client) {
						player = i;
						break;
					}
			name = lastStatus.getClientName(getResources(), c.client);
		} else {
			/* if we have advanced status, ignore all server messages (c == -1) */
			/* server messages are generated in serverStatus */
			if (lastStatus != null && lastStatus.isVersion(2))
				return;
			name = getString(R.string.client_d, c.client + 1);
		}

		final ChatEntry e = new ChatEntry(c.client, c.text, name);
		e.setPlayer(player);

		if (!client.spiel.is_local_player(player) &&
			(client.spiel.isStarted() || multiplayerNotification != null))
			updateMultiplayerNotification(true, e.toString());

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				chatEntries.add(e);
				if (c.client == -1)
					Toast.makeText(FreebloksActivity.this, "* " + c.text,
							Toast.LENGTH_LONG).show();
				else if (hasWindowFocus()) {
					/* only animate chatButton, if no dialog has focus */
					/* TODO: animate if activity is stopped or paused? */

					Animation a = new AlphaAnimation(0.4f, 1.0f);
					a.setDuration(350);
					a.setRepeatCount(Animation.INFINITE);
					a.setRepeatMode(Animation.REVERSE);
					chatButton.startAnimation(a);
				}
			}
		});
	}

	@Override
	public void gameStarted() {
		gameStartTime = System.currentTimeMillis();

		Bundle b = new Bundle();
		b.putString("server", client.getConfig().getServer());
		b.putString("game_mode", client.spiel.getGameMode().toString());
		b.putInt("w", client.spiel.width);
		b.putInt("h", client.spiel.height);
		b.putInt("clients", lastStatus.clients);
		b.putInt("players", lastStatus.player);

		FirebaseAnalytics.getInstance(this).logEvent("game_started", b);

		if (lastStatus.clients >= 2) {
			FirebaseAnalytics.getInstance(this).logEvent("game_start_multiplayer", b);
		}

		Log.d(tag, "Game started");
		for (int i = 0; i < Spiel.PLAYER_MAX; i++)
			if (client.spiel.is_local_player(i))
				Log.d(tag, "Local player: " + i);
	}

	@Override
	public void stoneUndone(@NonNull Turn t) {
		FirebaseAnalytics.getInstance(this).logEvent("undo_move", null);
	}

	@Override
	public void serverStatus(@NonNull NET_SERVER_STATUS status) {
		if (lastStatus != null && lastStatus.isVersion(2)) {
			/* generate server chat messages, aka "joined" and "left" */

			for (int i = 0; i < lastStatus.spieler.length; i++) {
				NET_SERVER_STATUS s;
				final int tid;
				if (lastStatus.spieler[i] < 0 && status.spieler[i] >= 0) {
					/* joined */
					s = status;
					tid = R.string.player_joined_color;
				} else if (lastStatus.spieler[i] >= 0 && status.spieler[i] < 0) {
					/* left */
					s = lastStatus;
					tid = R.string.player_left_color;
				} else continue;
				String name;
				name = s.getClientName(getResources(), s.spieler[i]);

				if (view == null)
					return;
				if (view.model.spiel == null)
					return;

				final String text = getString(tid, name, getResources().getStringArray(R.array.color_names)[view.model.getPlayerColor(i)]);
				final ChatEntry e = new ChatEntry(-1, text, name);
				e.setPlayer(i);

				if (!view.model.spiel.is_local_player(i))
					updateMultiplayerNotification(tid == R.string.player_left_color && client.spiel.isStarted(), text);

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						chatEntries.add(e);
					}
				});
			}
		}
		lastStatus = status;
		if (lastStatus.clients > 1) {
			chatButton.post(new Runnable() {
				@Override
				public void run() {
					chatButton.setVisibility(View.VISIBLE);
				}
			});
		}
	}

	@Override
	public void onConnected(@NonNull Spiel spiel) {

	}

	@Override
	public void onDisconnected(@NonNull Spiel spiel) {
		Log.w(tag, "onDisconnected()");
		final Exception error = (spielthread == null) ? null : spielthread.getError();
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				newCurrentPlayer(-1);

				if (error != null) {
					/* TODO: add sound on disconnect on error */
					saveGameState(GAME_STATE_FILE);

					AlertDialog.Builder builder = new AlertDialog.Builder(FreebloksActivity.this);
					builder.setTitle(android.R.string.dialog_alert_title);
					builder.setMessage(getString(R.string.disconnect_error, error.getMessage()));
					builder.setIcon(android.R.drawable.ic_dialog_alert);
					builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();

							try {
								canresume = restoreOldGame();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					builder.create().show();
				}
			}
		});
	}

	@Override
	public boolean commitCurrentStone(@NonNull final Turn turn) {
		if (client == null)
			return false;
		
		if (!client.spiel.is_local_player())
			return false;
		if (client.spiel.isValidTurn(turn) != Stone.FIELD_ALLOWED)
			return false;

		if (view.model.hasAnimations()) {
			Stone st = new Stone();
			st.copyFrom(client.spiel.getPlayer(turn.m_playernumber).get_stone(turn.m_stone_number));
			StoneRollEffect e = new StoneRollEffect(view.model, turn, view.model.currentStone.hover_height_high, -15.0f);

			EffectSet set = new EffectSet();
			set.add(e);
			set.add(new StoneFadeEffect(view.model, turn, 1.0f));
			view.model.addEffect(set);
		}

		view.model.soundPool.play(view.model.soundPool.SOUND_CLICK1, 1.0f, 0.9f + (float)Math.random() * 0.2f);
		vibrate(Global.VIBRATE_SET_STONE);

		client.set_stone(turn);
		return true;
	}

	@Override
	public void vibrate(int ms) {
		if (vibrate_on_move)
			vibrator.vibrate(ms);
	}

	@Override
	public void onBackPressed() {
		if (undo_with_back && client != null && client.isConnected()) {
			view.model.clearEffects();

			client.request_undo();

			view.model.soundPool.play(view.model.soundPool.SOUND_UNDO, 1.0f, 1.0f);
			return;
		}
		if (client != null && client.spiel.isStarted() && !client.spiel.isFinished() && lastStatus != null && lastStatus.clients > 1)
			showDialog(DIALOG_QUIT);
		else {
			if (view.model.intro != null) {
				view.model.intro.cancel();
			}
			else {
				if (client != null && client.isConnected())
					canresume = true;
				else
					canresume = false;
				showDialog(DIALOG_GAME_MENU);
			}
		}
	}

	@Override
	public void showPlayer(int player) {
		if (client == null)
			return;
		if (client.spiel == null)
			return;
		newCurrentPlayer(client.spiel.current_player());
	}

	String getPlayerName(int player) {
		String color_name = getResources().getStringArray(R.array.color_names)[view.model.getPlayerColor(player)];
		/* this will ensure that always the local name is used, even though the server
		 * might still have stored an old or no name at all
		 *
		 * When resuming a game, the name is lost and never set again. This is a non issue now.
		 */
		if (clientName != null && clientName.length() > 0 && client != null && client.spiel != null && client.spiel.is_local_player(player))
			return clientName;
		if (lastStatus == null)
			return color_name;
		return lastStatus.getPlayerName(getResources(), player, view.model.getPlayerColor(player));
	}

	Notification.Builder notificationBuilder;

	@RequiresApi(api = Build.VERSION_CODES.O)
	private void createNotificationChannels() {
		NotificationChannel channel = new NotificationChannel("default", getString(R.string.notification_channel_default), NotificationManager.IMPORTANCE_DEFAULT);
		channel.enableVibration(true);
		channel.enableLights(true);
		notificationManager.createNotificationChannel(channel);
	}

	void updateMultiplayerNotification(boolean forceShow, String chat) {
		if (client == null || client.spiel == null)
			return;
		if (!client.isConnected())
			return;
		if (multiplayerNotification == null && !forceShow)
			return;
		if (!show_notifications)
			return;
		if (spielthread == null)
			return;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannels();
		}
		
		Intent intent = new Intent(this, FreebloksActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		if (chat != null)
			intent.putExtra("showChat", true);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		if (notificationBuilder == null) {
			notificationBuilder = new Notification.Builder(this);
			
			notificationBuilder.setContentIntent(pendingIntent);
			
			if (Build.VERSION.SDK_INT >= 16) {
				notificationBuilder.addAction(android.R.drawable.ic_media_play, getString(R.string.notification_continue), pendingIntent);
				
				intent = new Intent(this, FreebloksActivity.class);
				intent.setAction(Intent.ACTION_DELETE);
				intent.addCategory(Intent.CATEGORY_DEFAULT);
				intent.putExtra("disconnect", true);
				PendingIntent disconnectIntent = PendingIntent.getActivity(getApplicationContext(), 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
				
				notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_disconnect), disconnectIntent);
			}
		}
		
		notificationBuilder.setContentTitle(getString(R.string.app_name))
			.setOngoing(false)
			.setDefaults(0)
			.setTicker(null)
			.setAutoCancel(true)
			.setSound(null);

		if (Build.VERSION.SDK_INT >= 16)
			notificationBuilder.setPriority(Notification.PRIORITY_DEFAULT);

		if ((forceShow && chat == null) || multiplayerNotification != null)
			notificationBuilder.setOngoing(true);

		if (!client.spiel.isStarted()) {
			notificationBuilder.setSmallIcon(R.drawable.notification_waiting_small);
			notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.appicon_small));
			notificationBuilder.setContentText(getString(R.string.lobby_waiting_for_players));
			notificationBuilder.setTicker(getString(R.string.lobby_waiting_for_players));
		} else if (client.spiel.isFinished()) {
			notificationBuilder.setSmallIcon(R.drawable.notification_your_turn);
			notificationBuilder.setContentText(getString(R.string.game_finished));
			notificationBuilder.setOngoing(false);
		} else {
			if (client.spiel.current_player() < 0)
				return;
			if (client.spiel.is_local_player()) {
				notificationBuilder.setSmallIcon(R.drawable.notification_your_turn);
				notificationBuilder.setContentText(getString(R.string.your_turn, getPlayerName(client.spiel.current_player())));
				notificationBuilder.setTicker(getString(R.string.your_turn, getPlayerName(client.spiel.current_player())));
				
				if (!forceShow) {
					notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);

					if (Build.VERSION.SDK_INT >= 16)
						notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
				}
			} else {
				notificationBuilder.setSmallIcon(R.drawable.notification_waiting_small);
				notificationBuilder.setContentText(getString(R.string.waiting_for_color, getPlayerName(client.spiel.current_player())));
				notificationBuilder.setTicker(getString(R.string.waiting_for_color, getPlayerName(client.spiel.current_player())));
			}
		}
		
		if (chat != null) {
			notificationBuilder.setSmallIcon(R.drawable.notification_chat);
			notificationBuilder.setContentText(chat);
			notificationBuilder.setTicker(chat);
			if (Build.VERSION.SDK_INT >= 16)
				notificationBuilder.setPriority(Notification.PRIORITY_HIGH);

			notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS);
			notificationBuilder.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.chat));
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			notificationBuilder.setChannelId("default");
		}

		Notification n;

		if (Build.VERSION.SDK_INT >= 16)
			n = notificationBuilder.build();
		else
			n = notificationBuilder.getNotification();
		
		if (chat == null)
			multiplayerNotification = n;
		
		notificationManager.notify(NOTIFICATION_GAME_ID, n);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (Intent.ACTION_DELETE.equals(intent.getAction())) {
			Log.d(tag, "ACTION_DELETE");
			finish();
			return;
		} else {
			if (intent.hasExtra("showChat") && client != null && client.spiel.isStarted())
				showDialog(DIALOG_LOBBY);
		}
		super.onNewIntent(intent);
	}

	@Override
	public void onSignInFailed() {

	}

	@Override
	public void onSignInSucceeded() {
		if (Global.IS_VIP) {
			unlock(getString(R.string.achievement_vip));
		}
	}
}
