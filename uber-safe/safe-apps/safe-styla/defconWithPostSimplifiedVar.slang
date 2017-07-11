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
     p(X) :- q(X).
     m(Z) :- n(Z).
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
    w(X) :- u(X), f($Arg).
    label("label/$Name").
  }.

defpost postLabeledSet(?Name, ?Arg) :- [setWithParametrizedLabel(?Name, ?Arg)].


defcon setWithParameterizedLabel(?NameComp0, ?NameComp1, ?Arg) :-
  {
    q($Arg).
    w(X) :- u(X), $Arg <: ipv4"152.3.136.26/24".
    t(Z) :- k(Z), f($NameComp0).
    label("label/$NameComp0/$NameComp1").
  }.

defpost postLabeledSet(?NameComp0, ?NameComp1, ?Arg) :- [setWithParameterizedLabel(?NameComp0, ?NameComp1, ?Arg)].


defcon exportPolicy(?PolicyId) :- 
  {
    r(X) :- t(X), w(Y).
    label($PolicyId).
  }.

defpost postPolicy(?PolicyId) :- [exportPolicy(?PolicyId)].

defguard testPolicy() :-
  ?PolicySet := label("policy102"),
  {
    link($PolicySet).
    t(alice).
    w(bob).
    r(X)?
  }.


defcon policyIPV4() :-
  {
    validIP(X) :- X <: ipv4"152.3.136.0/24".
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
    link($PolicyRef).
    h(fred).
    f(X) :- h(X), ipv4"152.3.136.26" <: ipv4"152.3.136.0/24".
    p(X)?
    m(X)?
    f(fred)?
  }.

definit mymethod().
