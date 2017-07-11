defcon policySnippet() :-
   spec('xxx'),
   {
     p(?X) :- q(?X).
     name("policy rule").
   }.

defcon mypolicy() :-
  {
    q(alice).
    //f(alice).
    name("my policy").
  }.

defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    import($RuleRef).
    import($PolicyRef).
    p(?X)?
  }.

//defguard mymethod(?X) :-
//  ?RuleRef   := policySnippet(),
//  ?PolicyRef := mypolicy(),
//  { 
//    import($RuleRef).
//    import($PolicyRef).
//    q($X).
//    p(?X)?
//  }.

//definit mymethod().
