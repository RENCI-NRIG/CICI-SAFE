defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/difc/keys/registry.pem').


defcon policySnippet() :-
   spec('xxx'),
   {
     p(?X) :- q(?X).
     m(?Z) :- n(?Z).
     label("policy rule").
   }.

defcon mypolicy() :-
  {
    q(david).
    q(alice).
    n(bob).
    label("my policy").
  }.

defcon exportPolicy(?PolicyId) :- 
  {
    r(?X) :- t(?X), w(?Y).
    label($PolicyId).
  }.

defpost postPolicy(?PolicyId) :- [exportPolicy(?PolicyId)].

defcon simpleIPV4() :-
  {
    inRange(?X) :- ?X <: ipv4"152.3.10.1/24".
    label("ipv4").
  }.

definit simpleIPV4().

defguard checkIPV4(?IP) :- 
  ?IPV4Policy := label("ipv4"),
  {
    link($IPV4Policy).
    inRange($IP)?
  }.

defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    link($RuleRef).
    link($PolicyRef).
    p(?X)?
    m(?X)?
  }.

//definit mymethod().
