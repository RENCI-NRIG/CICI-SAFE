defenv Selfie() :-
  spec('Mint a new key pair using RSA algorithm of size 4096 bits'),
  principal('/home/qiang/Desktop/safe-new-version-repo-working-copy/safe/safe-apps/safedebug/user2.pem').

defcon facts() :-
  spec('yyy'),
  {
    tagFile(file1, tag0).
    tagFile(file1, tag1).
    tagFile(file1, tag2).
    tagFile(file1, tag3).
    name("some facts").
  }.

defcon rules() :-
  spec('zzz'),
  {
    accessTag(?P, ?T) :- tagFile(?F, ?T), accessFile(?P, ?F).
    accessFile(alice, file1).
    accessTag(?P, ?T)?
    name("some rules").
  }.

defcon policySnippet() :-
  spec('xxx'),
  {
    p(?X) :- q(?X).
    m(?Y) :- n(?Y).
    v(?X) :- p(?X).
    v(?X) :- m(?X).
    name("policy rule").
  }.

defcon mypolicy() :-
  {
    accessTag(alice, tag0).
    accessTag(alice, tag1).
    accessTag(alice, tag2).
    accessTag(alice, tag3).
    q(bob).
    n(bob).
    name("my policy").
  }.

(* defguard mymethod(?X) :- *)
(* we cannot use \$RulesRef here for now*)
defguard mymethod() :-
  ?FactsRef := facts(),
  ?RulesRef := rules(),
  ?SubgoalsRef := inferSet(?RulesRef, ?FactsRef),  
  ?myPolicyRef := mypolicy(),
  ?policyRuleRef := policySnippet(),
  {
    import($SubgoalsRef). 
    import($myPolicyRef).
    import($policyRuleRef).
    v(bob)? =@= require 
  }.

definit mymethod().

(*    q($X).
    allow() :- p($X).
    allow()?
*)


    (* p(alice) =:= require *)
