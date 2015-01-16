/*
 * Copyright (c) 2015. Yrom Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.yrom.muzei.bili;

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;

import net.yrom.muzei.bili.BiliWallpaperService.Resolution;
import net.yrom.muzei.bili.BiliWallpaperService.Wallpaper;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;

import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.android.MainThreadExecutor;
import retrofit.client.ApacheClient;

/**
 * @auther yrom
 */
public class BiliWallpaperSource extends RemoteMuzeiArtSource {
    private static final int COMMAND_ID_SHARE = 1;
    private static final int COMMAND_ID_VIEW_MORE = 2;

    private static final String NAME = "Bilibili Wallpaper";
    private static final String TAG = "Bili-Wall";
    private static final int UPDATE_TIME_MILLIS = 3 * 60 * 60 * 1000; // 3 hours
    private static final int TIMEOUT_MS = 5000;

    private static HttpClient createDefaultClient() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, TIMEOUT_MS);
        HttpConnectionParams.setSoTimeout(params, TIMEOUT_MS);
        return new DefaultHttpClient(params);
    }

    private HttpClient mClient;
    private MainThreadExecutor mMainExecutor;
    private Executor mExecutor;

    public BiliWallpaperSource() {
        super(NAME);
        mClient = createDefaultClient();
        mMainExecutor = new MainThreadExecutor();
        mExecutor = AsyncTask.THREAD_POOL_EXECUTOR;
    }

    @Override
    protected void onUpdate(int reason) {
        List<UserCommand> commands = new ArrayList<>();
        if (reason == UPDATE_REASON_INITIAL) {
            // Show initial picture
            final File file = getCacheFile("66138");
            saveInitialPicture(file);
            if (file.exists()) {
                publishArtwork(new Artwork.Builder()
                        .imageUri(Uri.fromFile(file))
                        .title("22&33")
                        .token("66138")
                        .byline("Bilibili壁纸娘\n动漫")
                        .viewIntent(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("http://h.bilibili.com/wallpaper?action=detail&il_id=66138")))
                        .build());
                // show the latest photo in 15 minutes
                scheduleUpdate(System.currentTimeMillis() + 15 * 60 * 1000);
            } else {
                super.onUpdate(reason);
            }
        } else {
            super.onUpdate(reason);
        }

        commands.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        commands.add(new UserCommand(COMMAND_ID_SHARE, getString(R.string.action_share)));
        commands.add(new UserCommand(COMMAND_ID_VIEW_MORE, getString(R.string.view_more_info)));
        setUserCommands(commands);
    }

    private void saveInitialPicture(File file) {
        try {
            if (file.exists())
                file.delete();
            InputStream in = getApplicationContext().getAssets().open("2233.jpg");
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(file);
                copy(in, out);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (in != null)
                        in.close();
                } catch (IOException e) {
                }
                try {
                    if (out != null)
                        out.close();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCustomCommand(int id) {
        super.onCustomCommand(id);
        if (COMMAND_ID_SHARE == id) {
            Artwork currentArtwork = getCurrentArtwork();
            if (currentArtwork == null) {
                Log.w(TAG, "No current artwork, can't share.");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BiliWallpaperSource.this,
                                R.string.error_no_wallpaper_to_share,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }

            String detailUrl = "http://h.bilibili.com/wallpaper?action=detail&il_id="
                    + getCurrentArtwork().getToken();
            String artist = currentArtwork.getByline().split("\n")[0];

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "#Muze# 分享我的壁纸 \""
                    + currentArtwork.getTitle().trim()
                    + "\" by " + artist
                    + ". \n"
                    + detailUrl);
            shareIntent = Intent.createChooser(shareIntent, "分享壁纸");
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(shareIntent);

        } else if (COMMAND_ID_VIEW_MORE == id) {
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setData(Uri.parse("http://h.bilibili.com/wallpaper?action=detail&il_id="
                    + getCurrentArtwork().getToken()));
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(viewIntent);
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {

        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint("http://h.bilibili.com")
                .setExecutors(mExecutor, mMainExecutor)
                .setClient(new ApacheClient(mClient))
                .build();

        BiliWallpaperService service = adapter.create(BiliWallpaperService.class);
        List<Wallpaper> wallpapers = getWallpapers(service);
        if (wallpapers == null) {
            throw new RetryException();
        }
        if (wallpapers.isEmpty()) {
            Log.w(TAG, "No wallpapers returned from API.");
            scheduleUpdate(System.currentTimeMillis() + UPDATE_TIME_MILLIS);
            return;
        }
        wallpapers.remove(0); // first item is banner place holder
        final Wallpaper wallpaper = selectWallpaper(wallpapers);
        final Wallpaper selectedPaper = getDetail(service, wallpaper);
        if (selectedPaper == null) {
            Log.w(TAG, "No details returned for selected paper from API. id=" + wallpaper.il_id);
            throw new RetryException();
        }
        WallpaperManager manager = WallpaperManager.getInstance(getApplicationContext());
        int minimumHeight = manager.getDesiredMinimumHeight();
        final Resolution pic = selectResolution(selectedPaper, minimumHeight);

        publishArtwork(new Artwork.Builder()
                .imageUri((Uri.parse(pic.il_file.replaceFirst("_m\\.", "_l\\."))))
                .title(pic.title)
                .token(String.valueOf(wallpaper.il_id))
                .byline(wallpaper.author_name + ", "
                        + DateFormat.format("yyyy-MM-dd", wallpaper.posttime * 1000)
                        + "\n" + wallpaper.type)
                .viewIntent(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(wallpaper.author_url)))
                .build());
        scheduleUpdate(System.currentTimeMillis() + UPDATE_TIME_MILLIS);
    }

    private List<Wallpaper> getWallpapers(BiliWallpaperService service) {
        List<Wallpaper> wallpapers = null;
        for (int i = 0; i < 3; i++) {
            try {
                wallpapers = service.getWallpapers(1);
                break;
            } catch (RetrofitError e) {
                // fall through, need retry
                Log.d(TAG, "getWallpapers() RetrofitError.", e);
            } catch (Exception e) {
                // retry outside
                break;
            }
        }
        return wallpapers;
    }

    private Wallpaper getDetail(BiliWallpaperService service, Wallpaper wallpaper) {
        List<Wallpaper> wallpapers = null;
        for (int i = 0; i < 3; i++) {
            try {
                wallpapers = service.getDetail(wallpaper.il_id);
                break;
            } catch (RetrofitError e) {
                // fall through, need retry
                Log.d(TAG, "getDetail() RetrofitError. id=" + wallpaper.il_id, e);
            } catch (Exception e) {
                break;
            }
        }
        if (wallpapers == null) return null;
        for (Wallpaper paper : wallpapers) {
            if (paper.il_id == wallpaper.il_id) {
                return paper;
            }
        }
        return null;
    }

    private File getCacheFile(String pic) {
        final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "bili/" + pic + ".jpg");
        File parentFile = file.getParentFile();
        if (!parentFile.isDirectory()) {
            parentFile.delete();
            parentFile.mkdirs();
        }
        return file;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        String ids = getSharedPreferences().getString("shown_ids", null);
        if (ids != null) {
            String[] strs = ids.split(",");
            for (String str : strs) {
                if (str.length() > 0)
                    shownIds.add(Integer.valueOf(str));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StringBuilder builder = new StringBuilder();
        for (Integer id : shownIds) {
            builder.append(id.toString()).append(',');
        }
        getSharedPreferences().edit().putString("shown_ids", builder.toString()).commit();
        shownIds.clear();
        mClient.getConnectionManager().shutdown();
        mClient = null;
    }

    static Set<Integer> shownIds = new TreeSet<>();

    private Wallpaper selectWallpaper(List<Wallpaper> wallpapers) {
        int currentId = (getCurrentArtwork() != null) ? Integer.parseInt(getCurrentArtwork().getToken()) : 0;
        Random random = new Random();
        Wallpaper wallpaper;
        while (true) {
            int index = random.nextInt(wallpapers.size());
            wallpaper = wallpapers.get(index);

            if (shownIds.contains(wallpaper.il_id)) {
                continue;
            }

            if (wallpaper.detail.isEmpty()) {
                shownIds.add(wallpaper.il_id); // mark empty item as shown
                continue;
            }

            if (shownIds.size() >= wallpapers.size())
                shownIds.clear();

            if (currentId != wallpaper.il_id) {
                shownIds.add(wallpaper.il_id);
                break;
            }
        }
        return wallpaper;
    }

    private Resolution selectResolution(Wallpaper wallpaper, int minimumHeight) {
        Resolution pic = null;
        for (Resolution resolution : wallpaper.detail) {
            if (resolution.height >= minimumHeight) {
                pic = resolution;
                break;
            }
        }

        if (pic == null)
            pic = wallpaper.detail.get(wallpaper.detail.size() - 1);
        return pic;
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
    }
}
