# based on the Haml example by Tobias included with boot-jruby

# This script takes a reference to an adoc-file, converts them to adoc, and then
# writes the html-files as output.

# For documentation consider http://asciidoctor.org/docs/asciidoctor-diagram/

require 'asciidoctor'
require 'asciidoctor/cli'
require 'asciidoctor-diagram'

options = Asciidoctor::Cli::Options.new
#options[:safe]            = Asciidoctor::SafeMode::SAFE
options[:input_files]     = [$file]
options[:destination_dir] = ENV['BOOT_RSC_PATH'] + "/public" #fixme: hardcoded
#options[:attributes]      = 'beta'
#options[:backend]         = 'html5'
options[:header_footer]   = false
#options[:verbose]         = true
options[:base_dir]        = ENV['BOOT_RSC_PATH'] + "/public" #fixme: hardcoded

invoker = Asciidoctor::Cli::Invoker.new(options)
puts invoker.invoke!
exit invoker.code
