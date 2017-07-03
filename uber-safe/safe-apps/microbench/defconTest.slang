defcon makeMyKeySet() :- 
  spec('first set'),
  setWithName1{
     hello(world1).
  }
end

defguard query1() :-
  spec('simple query on in-memory set'),
  {
     import(setWithName1).
     hello(world1).
     hello(r"w.*")?
  }
end

defguard query2() :-
  spec('simple query on in-memory set'),
  {
     import(setWithName1).
     //hello(wunderland).
     hello(world2).
     hello(r"w.*")?
  }
end

defguard query3() :-
  spec('simple query on in-memory set'),
  {
     import(setWithName1).
     //hello(wonderful).
     hello(world3).
     hello(r"w.*")?
  }
end

definit makeMyKeySet(), query1(), query2(), query3().
