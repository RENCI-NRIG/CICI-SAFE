defcon policySnippet() :- 
   spec('xxx'), 
   { 
     p(X) :- q(X). 
     label("policy rule"). 
   }.

defcon mypolicy() :- 
  { 
    q(alice). 
    label("my policy"). 
  }.

defguard mymethod() :- 
  RuleRef   := policySnippet(),
  PolicyRef := mypolicy(),
  println(RuleRef),
  { 
    import($RuleRef).
    import($PolicyRef).
    p(alice)? 
  }.

definit mymethod().
