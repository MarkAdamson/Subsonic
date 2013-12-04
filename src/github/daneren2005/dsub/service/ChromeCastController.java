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
	private static final long STATUS_UPDATE_INTERVAL_SECONDS = 5L;
	
	private final Handler handler;
	private boolean running = false;
	private final TaskQueue tasks = new TaskQueue();
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> statusUpdateFuture;
	private final AtomicLong timeOfLastUpdate = new AtomicLong();
	private RemoteStatus remoteStatus;
	private float gain = 0.5f;
    
    public ChromeCastController(DownloadServiceImpl downloadService, Handler handler) {
    	this.downloadService = downloadService;
		this.handler = handler;
        new Thread() {
            @Override
            public void run() {
				running = true;
                processTasks();
            }
        }.start();
    }

	@Override
	public void start() {
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		tasks.add(new Start());
	}
	@Override
	public void stop() {
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		stopStatusUpdate();
		tasks.add(new Stop());
	}
	@Override
	public void shutdown() {
		running = false;
	}
	
	@Override
	public void updatePlaylist() {
	
	}
	@Override
	public void changePosition(int seconds) {
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		if (remoteStatus != null) {
			remoteStatus.setPositionSeconds(seconds);
		}
		tasks.add(new Skip(downloadService.getCurrentPlayingIndex(), seconds));
		downloadService.setPlayerState(PlayerState.STARTED);
	}
	@Override
	public void changeTrack(int index, DownloadFile song) {
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		tasks.add(new Skip(index, 0));
		downloadService.setPlayerState(PlayerState.STARTED);
	}
	@Override
	public void setVolume(boolean up) {
		float delta = up ? 0.1f : -0.1f;
		gain += delta;
		gain = Math.max(gain, 0.0f);
		gain = Math.min(gain, 1.0f);
		
		getVolumeToast().setVolume(gain);
		tasks.remove(SetGain.class);
		tasks.add(new SetGain(gain));
	}
	
	@Override
	public int getRemotePosition() {
		if (remoteStatus == null || remoteStatus.getPositionSeconds() == null || timeOfLastUpdate.get() == 0) {
			return 0;
		}
		
		if (remoteStatus.isPlaying()) {
			int secondsSinceLastUpdate = (int) ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L);
			return remoteStatus.getPositionSeconds() + secondsSinceLastUpdate;
		}
		
		return remoteStatus.getPositionSeconds();
	}
	
	private void processTasks() {
		while (running) {
			RemoteTask task = null;
			try {
				task = tasks.take();
				RemoteStatus status = task.execute();
				if(status != null && running) {
					onStatusUpdate(status);
				}
			} catch (Throwable x) {
				onError(task, x);
			}
		}
	}
	
	private synchronized void startStatusUpdate() {
		stopStatusUpdate();
		Runnable updateTask = new Runnable() {
		@Override
			public void run() {
				tasks.remove(GetStatus.class);
				tasks.add(new GetStatus());
			}
		};
		statusUpdateFuture = executorService.scheduleWithFixedDelay(updateTask, STATUS_UPDATE_INTERVAL_SECONDS,
			STATUS_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}
	
	private synchronized void stopStatusUpdate() {
		if (statusUpdateFuture != null) {
			statusUpdateFuture.cancel(false);
			statusUpdateFuture = null;
		}
	}
    
	private void onStatusUpdate(RemoteStatus remoteStatus) {
		timeOfLastUpdate.set(System.currentTimeMillis());
		this.remoteStatus = remoteStatus;
		
		// Track change?
		Integer index = remoteStatus.getCurrentPlayingIndex();
		if (index != null && index != -1 && index != downloadService.getCurrentPlayingIndex()) {
			downloadService.setPlayerState(PlayerState.COMPLETED);
			downloadService.setCurrentPlaying(index, true);
			if(remoteStatus.isPlaying()) {
				downloadService.setPlayerState(PlayerState.STARTED);
			}
		}
	}

	private void onError(RemoteTask task, Throwable x) {
		
	}
	
	private MusicService getMusicService() {
		return MusicServiceFactory.getMusicService(downloadService);
	}

	private class GetStatus extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			
		}
	}

	private class Skip extends RemoteTask {
		private final int index;
		private final int offsetSeconds;
		
		Skip(int index, int offsetSeconds) {
			this.index = index;
			this.offsetSeconds = offsetSeconds;
		}
		
		@Override
		RemoteStatus execute() throws Exception {
			
		}
	}

	private class Stop extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			
		}
	}

	private class Start extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			
		}
	}

	private class SetGain extends RemoteTask {
		private final float gain;
		
		private SetGain(float gain) {
			this.gain = gain;
		}
		
		@Override
		RemoteStatus execute() throws Exception {
			
		}
	}

	private class ShutdownTask extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			return null;
		}
	}
}
