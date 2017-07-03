
defguard authorize(?X) :- 
  {
     check(world).
     hello(?A) :- check(?A).
     hello($X)?
  }.
