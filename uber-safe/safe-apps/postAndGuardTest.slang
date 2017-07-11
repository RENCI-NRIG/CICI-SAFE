defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('/home/vamsi/Code/safe/geni/user2_keyPair.pem').

defenv Two :- hello, 2.

defcon makeMyKeySet() :-
  spec('Construct key set for self'),
  ?Two := 4, // Q: What is Self is redefined here? and published under Selfie's Self?
  {
    principal($SelfKey). // should throw an error if principal predicate is not present
    test($SelfKey).
    number($Two).
    hello(?X) :- check(?X).
  }.

defcon makeFunSet() :-
  spec('Construct key set for self'),
  ?Two := 4, // Q: What is Self is redefined here? and published under Selfie's Self?
  funSet{
    number($Two).
    hello(?X) :- check(?X).
    10.
    hello(world).
    pred().
  }.

defcon makeFunMergeSet() :-
  spec('Construct key set for self'),
  ?Y := makeFunSet(),
  funSet{
    number($Two).
    hello(?X) :- check(?X).
    simple(World).
    parent(a, b).
    parent(b, c).
    ancestor(?X, ?Y) :- parent(?X, ?Y).
    ancestor(?X, ?Y) :- parent(?X, ?Z), ancestor(?Z, ?Y).
  }.

(*
defcon makeFunMergeSet() :-
  spec('Construct key set for self'),
  funSet{
    number($Two).
    hello(?X) :- check(?X).
  }.
*)

defpost postASet() :- [makeFunMergeSet()].

defetch fetchASet() :- [computeIdFromName($Self, funSet)].

defguard guardTest() :- 
  ?X := fetchASet(),
  {
    $Self: importAll($X).
    //$Self: number(?N)?
    //simple(?X)?
    ancestor(?X, ?Y)?
  }.

//definit makeMyKeySet().
//definit makeFunSet(), makeFunMergeSet().
//definit makeFunSet(), makeFunMergeSet().
//definit makeFunSet(), postASet(), guardTest().
//definit makeFunSet(), postASet().
definit guardTest().

(* Three things troubling:
- String interpolation
- Local substitution
- Global substitution
*)
