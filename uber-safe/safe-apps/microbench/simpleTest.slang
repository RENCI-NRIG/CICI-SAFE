defenv Selfie :-
  //spec('Load the principal keypair'),
  principal('/home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/user1_keyPair.pem')
end

defenv Z :- 6.

defcon direct(?V) :- 
  ?SlangVar = slang,
  direct{
     fact(world).
     fact(one).
     fact(two).
     fact(three).
     fact($Self).
     fact($V).
     fact($SlangVar).
     delegation0(?A) :- fact(?A).
     delegation1(?A) :- delegation0(?A).
     delegation2(?A) :- delegation1(?A).
     delegation3(?A) :- delegation2(?A).
     delegation4(?A) :- delegation3(?A).
     delegation5(?A) :- delegation4(?A).
(*
     delegation6(?A) :- delegation5(?A).
     delegation7(?A) :- delegation6(?A).
     delegation8(?A) :- delegation7(?A).
     delegation9(?A) :- delegation8(?A).
     delegation10(?A) :- delegation9(?A).
     delegation11(?A) :- delegation10(?A).
     delegation12(?A) :- delegation11(?A).
     delegation13(?A) :- delegation12(?A).
     delegation14(?A) :- delegation13(?A).
     delegation15(?A) :- delegation14(?A).
     delegation16(?A) :- delegation15(?A).
     delegation17(?A) :- delegation16(?A).
     delegation18(?A) :- delegation17(?A).
     delegation19(?A) :- delegation18(?A).
     delegation(?A)   :- delegation19(?A).
*)
     goal(?X)         :- delegation4(?X).
  }
end

defguard factDB() :-
  ?X = world,
  ?Y = direct,
  println($Z),
  println($Self),
  println("hi there ?X and $Self and $Z; hello"),
  {
     import($Y).
     //goal($X)?
     goal(?G)?
  }
end

definit direct($Z), factDB().

defenv A :- apple.
