i want to make a full screen player view for this app

requirements:
- replace the current full player view with a full screen view (no other ui is visible), exitable via the back button
- support pause/play keys
- interpret left/right keys as time skips
- display a live transcript
  - subtitle lines are added to the bottom of the subtitle view as soon as the first word in a line is spoken
  - we have diarization (separate of speech by speaker) but not identification. each speaker does have a consistent identity throughout the transcript, so each speaker will have a generated avatar
  - the transcript state should react to time skips/navigation 

layout:
column(
	70% column(
		row-per-speaker(
			column(circular avatar) # avatar should float to the top as text grows
			column(transcript text)
		)
	)
	30% row(
		column(episode image)
		column(
			row(episode title)
			row(show)
			row(progress bar with times)
		)
	)
)
reasonable padding and margin is omitted. the transcript text should be large, around 5 rows of text on screen at a time.

for now, make up some fake transcript data/timecodes with speech segments of varying lengths to render as it plays.
