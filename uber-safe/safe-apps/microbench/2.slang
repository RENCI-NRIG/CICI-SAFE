
defguard authorize(?Y) :- 
  //println('this is a test'),
  //println(?Y),
  ?X := "?(Y)d",
  //println('this is a test -- end'),
  //println(?X),
  {
     fact(world).
     delegation1(?A) :- fact(?A).
     delegation2(?A) :- delegation1(?A).
     goal(?A)        :- delegation2(?A).
     goal($X)?
  }.

authorize($1)?
