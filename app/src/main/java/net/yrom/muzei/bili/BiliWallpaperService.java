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

import java.util.List;

import retrofit.http.GET;
import retrofit.http.Query;

/**
 * @auther yrom
 */

interface BiliWallpaperService {
    @GET("/wallpaperApi?action=getOptions&sort=rate_number")
    List<Wallpaper> getWallpapers(
            @Query(value = "page") int page
    );

    @GET("/wallpaperApi?action=getDetail")
    List<Wallpaper> getDetail(
            @Query(value = "il_id") int id);

    static class Wallpaper {
        int il_id;
        String author_name;
        String author_url;
        String type;
        long posttime;
        List<Resolution> detail;

        String getDetailUrl() {
            return "http://h.bilibili.com/wallpaper?action=detail&il_id=" + il_id;
        }
    }

    static class Resolution {
        String id;
        String il_file;
        int width;
        int height;
        String title;

    }
}
