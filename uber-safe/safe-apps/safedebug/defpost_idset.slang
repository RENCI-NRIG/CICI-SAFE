defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('/home/qiang/Desktop/safe-new-version-repo-working-copy/safe/safe-apps/safedebug/keys/user1.pem').
  //principal('/home/qiang/Desktop/EC2_cluster/AmpCamp.pem').
  //principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defcon makeMyIdSet() :-
  spec('Construct key set for self'),
  {
    principal($SelfKey).
    encoding('safe').
  }.

defpost postASet() :- [makeMyIdSet()].

defguard guardTest() :-
  {
    p(alice)?
  }.
