
defenv Selfie :-
  spec('Load the principal keypair'),
  principal?($1),
end

defcon addRecord?(?SetName, ?Speaker, ?SRN, ?IpAddress) :-
  "$SetName"{
    $Speaker: aRecord($SRN, $IpAddress).
  },
end

defguard q() :-
 {
   import!($2).
   aRecord(?Name, ?Address)?
 }
end

definit post?(addRecord?($2, $Self, $3, $4)).
