
defguard authorize(?X) :- 
  {
     fact(world).
     delegation1(?A)  :- fact(?A).
     delegation2(?A)  :- delegation1(?A).
     delegation3(?A)  :- delegation2(?A).
     delegation4(?A)  :- delegation3(?A).
     delegation5(?A)  :- delegation4(?A).
     delegation6(?A)  :- delegation5(?A).
     delegation7(?A)  :- delegation6(?A).
     delegation8(?A)  :- delegation7(?A).
     delegation9(?A)  :- delegation8(?A).
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
     delegation20(?A) :- delegation19(?A).
     delegation21(?A) :- delegation20(?A).
     delegation22(?A) :- delegation21(?A).
     delegation23(?A) :- delegation22(?A).
     delegation24(?A) :- delegation23(?A).
     delegation25(?A) :- delegation24(?A).
     delegation26(?A) :- delegation25(?A).
     delegation27(?A) :- delegation26(?A).
     delegation28(?A) :- delegation27(?A).
     delegation29(?A) :- delegation28(?A).
     delegation30(?A) :- delegation29(?A).
     delegation31(?A) :- delegation30(?A).
     delegation32(?A) :- delegation31(?A).
     delegation33(?A) :- delegation32(?A).
     delegation34(?A) :- delegation33(?A).
     delegation35(?A) :- delegation34(?A).
     delegation36(?A) :- delegation35(?A).
     delegation37(?A) :- delegation36(?A).
     delegation38(?A) :- delegation37(?A).
     delegation39(?A) :- delegation38(?A).
     delegation40(?A) :- delegation39(?A).
     delegation41(?A) :- delegation40(?A).
     delegation42(?A) :- delegation41(?A).
     delegation43(?A) :- delegation42(?A).
     delegation44(?A) :- delegation43(?A).
     delegation45(?A) :- delegation44(?A).
     delegation46(?A) :- delegation45(?A).
     delegation47(?A) :- delegation46(?A).
     delegation48(?A) :- delegation37(?A).
     delegation49(?A) :- delegation48(?A).
     delegation50(?A) :- delegation49(?A).
 
     goal(?A)         :- delegation2(?A).
     goal($X)?
  }.
