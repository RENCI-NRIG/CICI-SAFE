defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/naming-service/keys/na1.pem'). *)

defcon direct() :-
  {
     new(world).
     fact(world).
     fact(world1).
     delegation1021(?A)  :- new(?A).
     delegation1022(?A)  :- delegation1021(?A).
     delegation1023(?A)  :- delegation1022(?A).
     delegation1024(?A)  :- delegation1023(?A).
     delegation(?A)   :- delegation19(?A).
     goal(?X)         :- delegation1024(?X).
     name(directName).
  }.

defguard factDB(?Y) :-
  ?X = directName,
  {
     import($X).
     goal($Y)?
  }.

definit direct().

