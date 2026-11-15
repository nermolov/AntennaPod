the last chunk of work added a new full screen player, with dummy transcriptions.

we have separately built a transcription API service on another system, which you may access via ssh at taylor@192.168.10.11. the transcription system is in ~/playground/transcription on that machine.

create a plan to integrate this system, utilizing the diarized and word-level transcript. transcription can be initiated by opening the full screen player. if no transcript is stored for this episode, prompt the user to start a transcript job, then stop (pause?) playback and display a progress screen. once the job is done, save the results to our database (so they may be used when resuming the episode/to avoid extra compute) and resume playback with the transcript. replace the dummy transcript implementation we have right now.
