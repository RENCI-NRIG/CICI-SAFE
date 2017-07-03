defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/safe-styla/keys/IaaS.pem').

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

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

defcon setWithParametrizedLabel(?Name, ?Arg) :-
  {
    q($Arg).
    w(?X) :- u(?X), f($Arg).
    label("label/$Name").
  }.

defpost postLabeledSet(?Name, ?Arg) :- [setWithParametrizedLabel(?Name, ?Arg)].

defcon policyWithList(?Name, ?Arg) :-
  {
    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      ?Speaker: advertise(?DstIP,?Path,?AS),
      ownPrefix(?Speaker,?DstIPSet),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Speaker,?DstIP,?Path,?AS):-
      eq([?Head|?Tail],Path),
      ?Speaker:advertise(?DstIP,?Path,?AS),
      authorizedAdvertise(?P,?DstIPSet,?Tail,?Speaker),
      ?DstIP <: ?DstIPSet.

    q($Arg).
    w(?X) :- u(?X), f($Arg).
    label("label/$Name").
  }.

defpost postSetWithList(?Name, ?Arg) :- [policyWithList(?Name, ?Arg)].


defcon setWithParameterizedLabel(?NameComp0, ?NameComp1, ?Arg) :-
  {
    q($Arg).
    w(?X) :- u(?X), $Arg <: ipv4"152.3.136.26/24".
    t(?Z) :- k(?Z), f($NameComp0).
    label("label/$NameComp0/$NameComp1").
  }.

defpost postLabeledSet(?NameComp0, ?NameComp1, ?Arg) :- [setWithParameterizedLabel(?NameComp0, ?NameComp1, ?Arg)].


defcon exportPolicy(?PolicyId) :- 
  {
    r(?X) :- t(?X), w(?Y).
    label($PolicyId).
  }.

defpost postPolicy(?PolicyId) :- [exportPolicy(?PolicyId)].

defguard testPolicy() :-
  ?PolicySet := label("policy102"),
  {
    link($PolicySet).
    t(alice).
    w(bob).
    r(?X)?
  }.


defcon policyIPV4() :-
  {
    validIP(?X) :- ?X <: ipv4"152.3.136.0/24".
    label("ipv4-policy").
  }.

defpost postPolicyIPV4() :- [policyIPV4()].

defguard testipv4(?IP) :-
  ?PolicySet := label('ipv4-policy'),
  {
    link($PolicySet).
    validIP($IP)? 
  }.

defcon policyScid() :-
  {
    validScid(?X) :- ?Root := rootPrincipal(?X), ?Root = "alice".
    label("scid-test-policy").
  }.

defpost postScidPolicy() :- [policyScid()].

defguard testScid(?Scid) :-
  ?PolicySet := policyScid(), 
  {
    link($PolicySet).
    validScid($Scid)? 
  }.

defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    link($RuleRef).
    link($PolicyRef).
    h(fred).
    f(?X) :- h(?X), ipv4"152.3.136.26" <: ipv4"152.3.136.0/24".
    p(?X)?
    m(?X)?
    f(fred)?
  }.

defguard testComments(?Subject) :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    link($RuleRef).
    link($PolicyRef).
    h(fred).
    f(?X) :- h(?X), ipv4"152.3.136.26" <: ipv4"152.3.136.0/24".
    p(?X)?
    m(?X)?
    f($Subject)?
//  g($Object).
//  h(?UnusedVar) :- g(?UnusedVar).
  }.

defguard testUnderscore() :- {
  edge(a, b, 3).
  edge(b, c, 4).
  path(X, Z, W) :- edge(X, Y, W1), path(Y, Z, W2), W is W1 + W2. 
  path(X, Y, W) :- edge(X, Y, W).
  path(X, Y) :- path(X, Y, _).
  path(X, Y, W, _) :- path(X, Y, W).
  path(a, c, W)?
  path(a, c)?
  path(a, c, W, rdm)?
  //alice: path(X, Y, W)?
}.

defguard testStylaLib(?A,?B) :-
   {
    edge(a,b,3).
    edge(b,c,1).
    edge(c,d,1).
    edge(b,a,3).
    edge(c,b,1).
    edge(d,c,1).
    path(X,Y) :- path(X,Y,[]).
    path(X,Y,V) :- \+(member(X,V)),edge(X,Y,W),traceln(Y),traceln(V).
    path(X,Y,V) :- \+(member(X,V)), edge(X,Z,W),traceln(Z),Z \==(Y),path(Z,Y,[X|V]), X \==(Y).
    path($A,$B)?
  }.

defguard testIneq(?A,?B) :-
   {
    edge(a,b,3).
    validEdge(X, Y) :- edge(X, Y, W), W \== 2.
    validEdge($A, $B)?
  }.

defguard testIneqSimple(?A) :-
  {
   q(?A):- ?A \== 3.
   q($A)?
  }.

defguard testIneqString(?A) :-
  {
   q(?A):- ?A \== "abc".
   q($A)?
  }.

defguard testsimple(?A):-
 {
    n(a,b).
    n(a,c).
    n(a,d).
    ap(?X,[?Head|?Loop1]):- traceln(?X),n(?X,?Head),ap(?X,?Loop1).
    ap(?X,[]).
    ap($A,[b,c,d])?
  }.

defguard passList(?A, ?B):-
 {
    n(a,b).
    n(a,c).
    n(a,d).
    ap(?X,[?Head|?Loop1]):- traceln(?X),n(?X,?Head),ap(?X,?Loop1).
    ap(?X,[]).
    ap($A,$B)?
  }.

defguard testLast():-
  {
    eat(A,Tail):-
      love(A,C),
      last(C,Tail).

    love("alice",["apple","banana"]).
    eat("alice", "banana")?
    is_full_connected() :- type(?Node,"ComputeElement"),is_full_connected2(?Node).
  }.


defguard testNegation():-
  {
    edge(a, b).
    edge(a, c).
    edge(c, d).
    //not_connected(X) :- \+(edge(X, Y)).
    //not_connected(a)? 
    \+(edge(f,X))?
  }.


defguard dynamicConjunction(?N) :-
  {
    node(a).
    node(b).
    node(c).
    node(d).
    edge(a, b).
    edge(a, c).
    edge(c, d).
    edge(d, c).
    edge(d, a).
    edge(c, b).
    edge(d, b).
    edge(d, d).
    
    //not_connected(X) :- \+(edge(X, Y)).
    //not_connected(a)? 
    not_connected_to(A, X) :- node(X), \+(edge(A, X)).
    not_fully_connected(A) :- not_connected_to(A, X).
    connected_to_all(A) :- \+(not_fully_connected(A)).
    connected_to_all($N)? 
  }.


defguard tagAccessTest(?U) :-
  {
    hasTag(file, t0).
    hasTag(file, t1).
    hasTag(file, t2).
    hasTag(file, t3).
    hasAccessForTag(bob, t0).
    hasAccessForTag(bob, t1).
    hasAccessForTag(bob, t2).
    hasAccessForTag(bob, t3).
    hasAccessForTag(alice, t1).
    hasAccessForTag(alice, t2).
    hasAccessForTag(alice, t3).

    noAccess(?P, ?File, ?T) :- hasTag(?File, ?T), \+(hasAccessForTag(?P, ?T)). 
    missingAccessOfTags(?P, ?File) :- noAccess(?P, ?File, ?T).
    accessOfAllTags(?P, ?File) :- \+(missingAccessOfTags(?P, ?File)).
    canAccessFile(?P, ?File) :- accessOfAllTags(?P, ?File).
    
    canAccessFile($U, file)?
  }.


definit mymethod().
