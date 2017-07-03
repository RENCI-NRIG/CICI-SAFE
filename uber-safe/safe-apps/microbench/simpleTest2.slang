defenv G :- global1.
defenv H :- global2.

defcon test(?A) :-
  ?G = local,
  test{
    fact($A).
    fact($G). 
    fact($H).
    fact2($A).
  }
end

defguard query() :-
  {
    import(test).
    fact(?Z)?
    fact2(?Z)?
  }
end

definit test(passByValue), query().
