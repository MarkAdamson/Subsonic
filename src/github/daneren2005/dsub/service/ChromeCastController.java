/*
  This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2009 (C) Sindre Mehus
*/

package github.daneren2005.dsub.service;

import android.os.Handler;
import android.util.Log;

import github.daneren2005.dsub.R;
import github.daneren2005.dsub.domain.RemoteStatus;
import github.daneren2005.dsub.domain.PlayerState;
import github.daneren2005.dsub.domain.RemoteControlState;
import github.daneren2005.dsub.service.parser.SubsonicRESTException;
import github.daneren2005.dsub.util.Util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ChromeCastController extends RemoteController {
	private static final String TAG = ChromeCastController.class.getSimpleName();
	
	private MediaProtocolMessageStream messageStream;
	
	private DownloadFile currentPlaying;
	private float gain = 0.5f;
    
	public ChromeCastController(DownloadServiceImpl downloadService, Handler handler) {
		this.downloadService = downloadService;
		messageStream = new MediaProtocolMessageStream();
		
		DownloadFile currentPlaying = downloadService.getCurrentPlaying();
		changeTrack(0, currentPlaying);
	}

	@Override
	public void start() {
		messageStream.play();
	}
	@Override
	public void stop() {
		messageStream.stop();
	}
	@Override
	public void shutdown() {
		running = false;
	}
	
	@Override
	public void updatePlaylist() {
		DownloadFile currentPlaying = downloadService.getCurrentPlaying();
		if(this.currentPlaying != currentPlaying) {
			changeTrack(0, currentPlaying);
		}
	}
	@Override
	public void changePosition(int seconds) {
		messageStream.playFrom(seconds);
	}
	@Override
	public void changeTrack(int index, DownloadFile songFile) {
		currentPlaying = songFile;
		MusicDirectory.Entry song = songFile.getSong();
		ContentMetadata metadata = new ContentMetadata();
		metadata.setTitle(song.getTitle());
		// TODO: Setup image id as URI
		// metadata.setImageUrl();
		
		MediaProtocolCommand cmd = messageStream.loadMedia(song.getId(), metadata);
		cmd.setListener(new MediaProtocolCommand.Listener() {
			@Override
			public void onCompleted(MediaProtocolCommand mPCommand) {
				start();
			}
			
			@Override
			public void onCancelled(MediaProtocolCommand mPCommand) {
				
			}
		});
	}
	@Override
	public void setVolume(boolean up) {
		float delta = up ? 0.1f : -0.1f;
		gain += delta;
		gain = Math.max(gain, 0.0f);
		gain = Math.min(gain, 1.0f);
		
		getVolumeToast().setVolume(gain);
		messageStream.setVolume(gain);
	}
	
	@Override
	public int getRemotePosition() {
		return 0;
	}
	
	public MediaProtocolMessageStream getMessageStream() {
		return messageStream;
	}

	private void onError(RemoteTask task, Throwable x) {
		
	}
	
	private MusicService getMusicService() {
		return MusicServiceFactory.getMusicService(downloadService);
	}
}
