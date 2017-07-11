(/*
defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('RSA', 512).
*)

defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

(*
defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  newPrincipal('RSA', 1024, '/home/vamsi/Code/safe/geni/user2_keyPair.pem').

  {
    recipient(id, publicKey, sharedKeyLocation, encryptionAlgorithm).
  }
*)

defcon makeMyKeySet() :-
  spec('Construct key set for self'),
  {
    principal($SelfKey).
    encoding('safe').
    $Self: fact(hello, world).
    value($Self).
    number($Two).
    rule(?X, ?Y) :- alice: rule(?X, ?Z), ?X: fact(?Z, ?Y).
    //name().
    bigNumber(102).
  }.

defcon makeAnotherSet() :- 
  'test'{
     encoding('safe').
     parent(a, b).
     parent(b, c).
     parent(c, d).
     ancestor(?X, ?Y) :- parent(?X, ?Y).
     ancestor(?X, ?Z) :- parent(?X, ?Z), ancestor(?Z, ?Y).
   }.

defcon makeEncryptionSet() :-
  'etest'{
     subject($Self, $SelfKey, '/home/vamsi/Code/safe/geni/sharedKey.txt', 'AES').
     encoding('slang').
     parent(a, b).
     parent(b, c).
     parent(c, d).
     ancestor(?X, ?Y) :- parent(?X, ?Y).
     ancestor(?X, ?Z) :- parent(?X, ?Z), ancestor(?Z, ?Y).
   }.

defcon immutableTest() :-
  spec('this is an immutable set'),
  {{
     fact(never, changes).
     rule(also, ?X) :- never(changes, ?X).
     image.
  }}.

defcon speaksForTest() :-
  'duke'{
    speaker(nil, 'ArZAd-4x4dDRQiTk_tiGq2kgRTmwBr4gnie-7jAwtF4').
    subject('1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil, nil, nil). 
    hello(world).
    filler(fact).
  }.

defcon 
  'dukeOn'{
    speaker(nil, 'dBVmem6ZwkgEptQVvEIOgrAYU9sYxwOipiwYZiFNJ5w').
    subject('1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps', nil, nil, nil). 
    hello(world).
    filler(fact).
  }.

//defetch fetchTest() :- ['NYqSDey2KzPAgu6XbpfwEytazmjddTNHj-0XU65ts4Y']. // speaksForOn
defetch fetchTest() :- ['_9TQx3WZqArIvaQwnPT61iHK5gdzzeTNRn9mpriwrqk']. // speaksFor
//defetch fetchTest() :- ['DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'].
//defetch fetchTest() :- ['ArZAd-4x4dDRQiTk_tiGq2kgRTmwBr4gnie-7jAwtF4'].
//defetch fetchTest() :- ['1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'].

defenv Two :- hello, 2.

defguard guardTest() :- 
  ?Closure := fetchTest(),
  {
     importAll(?Closure).
     check(working).
     filler(?Y) :- ?X: filler(?Y).
     filler(?A)?
  }.


//defpost publishNestedData() :- [makeMyKeySet()].
//defpost publishNestedData() :- [makeMyKeySet(), makeAnotherSet()].
//defpost publishNestedData() :- [makeAnotherSet()].
//defpost publishNestedData() :- [makeEncryptionSet()].
//defpost publishNestedData() :- [immutableTest()].
//defpost publishNestedData() :- [speaksForTest()].
//defpost publishNestedData() :- [speaksForOnTest()].

definit makeMyKeySet().
//definit makeAnotherSet().
//definit makeEncryptionSet().
//definit publishNestedData().
//definit fetchTest().
//definit guardTest().
