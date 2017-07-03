defenv Selfie() :-
  spec('Load the principal keypair'),
  principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/safe-styla/keys/IaaS.pem').

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon tagObject(?Object, ?Tag) :-
  {
    tag($Object, $Tag). 
    label("$Object").
  }.

defpost postObjectTag(?Object, ?Tag) :- [tagObject(?Object, ?Tag)].

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

defguard testPolicy2() :-
  {
    link('e28rzcd-FtjxCG3YMG-8Xkt6TGoNO_i4jc4Ju5c_YjI').
    'ZiXERAE5DwiXmo6QYVZYh7R6XMftfgA-zzB9Qkyonhg': t(alice).
    'ZiXERAE5DwiXmo6QYVZYh7R6XMftfgA-zzB9Qkyonhg': w(bob).
    'ZiXERAE5DwiXmo6QYVZYh7R6XMftfgA-zzB9Qkyonhg': r(?X)?
  }.


defcon setForTestingRetraction() :-
  {
    hasInstance(instance1).
    hasInstance(instance2).
    hasInstance(instance3). 
    label("test-retraction").
  }.

defpost postSetForTestingRetraction() :- [setForTestingRetraction()].

defcon setWithRetraction() :- 
  {
    hasInstance(instance2)~
    label("test-retraction").
  }.

defpost postRetraction() :- [setWithRetraction()].

defguard checkRetracted() :-
  ?Set := label("test-retraction"),
  {
    link($Set).
    hasInstance(instance2)? 
  }.



defcon policyIPV4() :-
  {
    validIP(?X) :- ?X <: ipv4"152.3.136.0/24".
    label("ipv4-policy").
  }.

defguard testipv4(?IP) :-
  ?PolicySet := policyIPV4(),
  {
    link($PolicySet).
    validIP($IP)? 
  }.


defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    link($RuleRef).
//    link($PolicyRef).
    h(fred).
    f(?X) :- h(?X), ipv4"152.3.136.26" <: ipv4"152.3.136.0/24".
    p(?X)?
    m(?X)?
    f(fred)?
  }.

defguard queryIP(?X) :-
  { 
    q(?X) :- ?X <: ipv4"152.3.136.0/24".
    q($X)?
    // ip address testing
  }.

definit mymethod().

defguard test102(?A) :-
  {
    a(?A) :- member(?A,[a,b,c,d]).
    a($A)?
  }.

defguard testListLength(?A) :-
  {
    length($A, ?L)?
  }.

defguard testListLength2(?A, ?L) :-
  {
    length($A, $L)?
  }.
