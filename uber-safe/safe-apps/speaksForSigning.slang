defenv Selfie() :-
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defenv Subject() :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv SpeaksForRef() :- u'Sq3Q1eBAzv-YddnfO0PShr5HYEwV_yOoFS_eVufroiM'.

defcon speakerSigningForSubjectTest() :-
  spec('test for speaker (issuer) signing the set for subject'),
  caTest{
    subject($Subject, nil, $SpeaksForRef). // ignores SpeaksForRef
    hello(speaksFor).
    said(?X) :- hello(?X).
  }.

defcon speakerSigningForSubject() :-
  spec('test for speaker (issuer) signing the set for subject'),
  ca{
    subject($Subject, nil).
    speaker(nil, $SpeaksForRef).
    hello(speaksFor).
    said(?X) :- hello(?X).
  }.

defcon negativeSpeakerSigningForSubject() :-
  spec('test for speaker (issuer) signing the set for subject'),
  negativeCa{
    subject($Subject, nil).
    speaker(nil, $Subject).
    hello(speaksFor).
    said(?X) :- hello(?X).
  }.


defpost post() :- [speakerSigningForSubject(), negativeSpeakerSigningForSubject()].

definit post().
