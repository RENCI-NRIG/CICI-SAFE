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
    name("policy rule").
  }.

defcon mypolicy() :-
  {
    q(alice).
    q(bob).
    name("my policy").
  }.

(* defguard mymethod(?X) :- *)
(* we cannot use \$\RulesRef here for now*)
defguard mymethod() :-
  ?FactsRef := facts(),
  ?RulesRef := rules(),
  ?SubgoalsRef := inferSet(?RulesRef, ?FactsRef),  
  ?AnnotatedSubgoalsRef := querySet(?SubgoalsRef, require),
  {
    import($FactsRef). 
    import($AnnotatedSubgoalsRef).
  }.

definit mymethod().

(*    q($X).
    allow() :- p($X).
    allow()?
*)


    (* p(alice) =:= require *)
