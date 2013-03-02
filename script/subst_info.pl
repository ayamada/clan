#!perl --
use strict;
use warnings;

sub usage {
  print "usage: perl $0 path release_flag\n";
  print "  path: path/to/Info.java\n";
  print "  release_flag: 0 or 1\n";
}

sub convert_type {
  my $type = shift;
  my $val = shift;
  return undef if not defined $val;
  my $solver = {
    boolean => sub { shift() ? 'true' : 'false' },
    String => sub { my $s = shift; $s =~ s/\"/\\"/g; '"'.$s.'"' },
    int => sub { int(shift) },
    # TODO: 必要になり次第追加する事
  }->{$type};
  die "cannot convert type $type\n" if not defined $solver;
  $solver->($val);
}

sub oneline { my $s = shift; chomp $s; $s =~ s/\n|\r/ /g; $s }

sub main {
  my $path = shift;
  my $release_flag = shift;
  # パラメータチェック
  return usage() if not defined $path;
  return usage() if not defined $release_flag;
  die("path $path not found\n") if not -e $path;
  print "start.\n";
  # 置換パラメータの用意
  my $subst_table = {
    debug => (not $release_flag),
    buildNumber => oneline(`LANG=C date +%s.%N`),
    buildDate => oneline(`LANG=C date`),
    buildEnv => oneline(`uname -a`),
    buildCompilerVersion => oneline(`javac -version 2>&1`),
  };
  # 置換処理
  my $tmpfile = $path . "_$$.tmp";
  open(my $src_fh, '<', $path) or die "cannot open srcfile $path\n";
  open(my $dst_fh, '>', $tmpfile) or die "cannot open tmpfile $tmpfile\n";
  eval {
    while (1) {
      my $line = <$src_fh>;
      last if not defined $line;
      if ($line =~ /^(\s+public static volatile) (\w+) (\w+) (\=) (.*)(\;\n)\z/) {
        my ($psf, $type, $var, $eq, $val, $delim) = ($1, $2, $3, $4, $5, $6);
        my $new_val = $subst_table->{$var};
        if (defined $new_val) {
          $new_val = convert_type($type, $new_val);
          $line = "$psf $type $var $eq $new_val$delim";
        }}
      print $dst_fh $line;
    }
    close($src_fh) or die "cannot close srcfile $path\n";
    close($dst_fh) or die "cannot close tmpfile $tmpfile\n";
  };
  if ($@) {
    my $e = $@;
    unlink $tmpfile;
    die $e;
  }

  rename $tmpfile, $path;
  print "done.\n";
}

main($ARGV[0], $ARGV[1]);

# vim:set ft=perl ts=2 sts=2 sw=2 et:
